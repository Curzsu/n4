package com.n4monitor.transfer.deployment;

import com.n4monitor.alarm.service.AlarmRuleSyncService;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import com.n4monitor.transfer.ImportStats;
import com.n4monitor.transfer.ImportSummary;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class DeploymentTransferService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTransferService.class);
    private static final String KEY_SEPARATOR = "\u0000";
    private static final int DEFAULT_NAV_MAX_LEVEL = 2;
    private static final int POINT_BATCH_SIZE = 500;
    private static final int POINT_LOOKUP_BATCH_SIZE = 5000;

    private final DeploymentCsvParser csvParser;
    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;
    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final PointDeploymentMapper pointDeploymentMapper;
    private final NavNodeMapper navNodeMapper;
    private final com.n4monitor.alarm.mapper.AlarmRuleEnumMapper alarmRuleEnumMapper;
    private final com.n4monitor.alarm.mapper.AlarmRuleNumericMapper alarmRuleNumericMapper;
    private final AlarmRuleSyncService alarmRuleSyncService;
    private final ExecutorService redisSyncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "deployment-redis-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final Object redisSyncMonitor = new Object();
    private boolean redisSyncPending;
    private boolean redisSyncRunning;

    @Transactional
    public ImportSummary importFiles(List<MultipartFile> files, boolean dryRun) {
        ImportStats stats = new ImportStats(dryRun);
        List<DeploymentCsvData> csvFiles = parseFiles(files, stats);
        stats.sourceFileCount(csvFiles.size());
        stats.sourceRowCount(csvFiles.stream().mapToInt(file -> file.rows().size()).sum());
        if (!stats.valid() || csvFiles.isEmpty()) {
            if (csvFiles.isEmpty() && stats.valid()) {
                stats.error("EMPTY_INPUT", "No CSV files were provided", null, null);
            }
            return stats.toSummary();
        }

        List<DeploymentCsvRow> rows = csvFiles.stream()
                .flatMap(file -> file.rows().stream())
                .toList();
        List<DeviceTemplate> templates = deviceTemplateMapper.findAllIncludingDeleted();
        List<PointTemplate> pointTemplates = pointTemplateMapper.findAllIncludingDeleted();
        List<DeviceDeployment> deployments = deviceDeploymentMapper.findAllIncludingDeleted();
        List<PointDeployment> pointDeployments = findExistingPointDeployments(rows);
        List<NavNode> navNodes = new ArrayList<>(navNodeMapper.findAll());

        Map<String, DeviceTemplate> templatesByN4Name = indexTemplatesByN4Name(templates);
        Map<String, PointTemplate> pointsByTemplateAndName = indexPointTemplates(pointTemplates);
        Map<String, DeviceDeployment> deploymentsByBusinessKey = indexDeployments(deployments);
        Map<String, PointDeployment> pointsByN4Address = indexPointDeployments(pointDeployments);

        Map<String, DevicePlan> devicePlans = new LinkedHashMap<>();
        Map<String, PointPlan> pointPlans = new LinkedHashMap<>();
        Set<String> seenN4Addresses = new HashSet<>();
        Set<String> warnedNavigationPaths = new HashSet<>();
        Set<Long> backfilledPointTemplateIds = new HashSet<>();
        for (DeploymentCsvRow row : rows) {
            try {
                String devicePath = required(row.devicePath(), "*path");
                String pointName = required(row.pointName(), "*name");
                String n4Address = required(row.mqttPath(), "mqttPath");
                required(row.allPath(), "allPath");
                String n4Name = row.n4Name();
                DeviceTemplate template = templatesByN4Name.get(n4Name);
                if (template == null || Boolean.TRUE.equals(template.getIsDeleted())) {
                    stats.error(
                            "MISSING_DEVICE_TEMPLATE",
                            "No active device template matches n4_name '" + n4Name + "'",
                            row.sourceFile(),
                            row.rowNumber());
                    continue;
                }
                if (!seenN4Addresses.add(n4Address)) {
                    stats.error(
                            "DUPLICATE_MQTT_PATH",
                            "mqttPath is duplicated in the import batch: " + n4Address,
                            row.sourceFile(),
                            row.rowNumber());
                    continue;
                }

                String systemName = row.systemName();
                String deviceKey = deviceKey(systemName, devicePath);
                DevicePlan devicePlan = devicePlans.get(deviceKey);
                if (devicePlan == null) {
                    Long navNodeId = resolveNavigationNodeId(systemName, navNodes);
                    if (navNodeId == null) {
                        if (canEnsureNavigationPath(systemName, navNodes)) {
                            stats.navigationGroupCreated(navigationGroupName(systemName));
                        } else if (warnedNavigationPaths.add(systemName)) {
                            stats.warning(
                                    "NAVIGATION_NODE_NOT_FOUND",
                                    "No navigation root can contain system path '" + systemName
                                            + "'; device will remain unmounted",
                                    row.sourceFile(),
                                    row.rowNumber());
                        }
                    }
                    DeviceDeployment existingDeployment = deploymentsByBusinessKey.get(deviceKey);
                    if (!isActiveDeployment(existingDeployment, navNodeId)) {
                        stats.deviceDeploymentCreated();
                    } else {
                        stats.deviceDeploymentReused();
                    }
                    devicePlan = new DevicePlan(
                            devicePath,
                            row,
                            template,
                            existingDeployment,
                            navNodeId,
                            deviceKey);
                    devicePlans.put(deviceKey, devicePlan);
                } else if (!devicePlan.systemName().equals(systemName)) {
                    stats.error(
                            "DEVICE_PATH_CONFLICT",
                            "The same *path maps to multiple system paths",
                            row.sourceFile(),
                            row.rowNumber());
                    continue;
                }

                String pointKey = n4Address;
                PointTemplate pointTemplate = pointsByTemplateAndName.get(
                        pointKey(template.getDeviceTmplId(), pointName));
                if (pointTemplate == null || Boolean.TRUE.equals(pointTemplate.getIsDeleted())) {
                    stats.warning(
                            "MISSING_POINT_TEMPLATE",
                            "Point template not found for n4_name '" + n4Name
                                    + "' and point '" + pointName + "'; row skipped",
                            row.sourceFile(),
                            row.rowNumber());
                    stats.skippedRow();
                    continue;
                }
                if (pointPlans.containsKey(pointKey)) {
                    stats.error(
                            "DUPLICATE_MQTT_PATH",
                            "mqttPath is duplicated in the import batch: " + n4Address,
                            row.sourceFile(),
                            row.rowNumber());
                    continue;
                }

                PointDeployment existingPoint = pointsByN4Address.get(n4Address);
                if (existingPoint == null) {
                    stats.pointDeploymentCreated();
                } else {
                    stats.pointDeploymentReused();
                }
                String normalizedUnit = normalizeUnit(row.unit());
                boolean needsTemplateBackfill = pointTemplate.getType() == null
                        || (pointTemplate.getUnit() == null && normalizedUnit != null);
                if (needsTemplateBackfill && backfilledPointTemplateIds.add(pointTemplate.getPointTmplId())) {
                    stats.pointTemplateBackfilled();
                }
                if (pointTemplate.getType() == null) {
                    pointTemplate.setType(row.type());
                }
                if (pointTemplate.getUnit() == null && normalizedUnit != null) {
                    pointTemplate.setUnit(normalizedUnit);
                }
                pointPlans.put(pointKey, new PointPlan(
                        row, devicePlan, pointTemplate, existingPoint, needsTemplateBackfill));
            } catch (IllegalArgumentException exception) {
                stats.error("INVALID_ROW", exception.getMessage(), row.sourceFile(), row.rowNumber());
            }
        }

        AlarmState alarmState = buildAlarmState(pointPlans.values(), stats);

        Map<String, Integer> pointCountByDevice = new HashMap<>();
        for (PointPlan pointPlan : pointPlans.values()) {
            pointCountByDevice.merge(pointPlan.devicePlan().deviceKey(), 1, Integer::sum);
        }
        for (DevicePlan devicePlan : devicePlans.values()) {
            boolean isNew = !isActiveDeployment(devicePlan.existingDeployment(), devicePlan.navNodeId());
            String action = isNew ? "CREATE" : "REUSE";
            String templateName = devicePlan.template() != null ? devicePlan.template().getDeviceTmplName() : "";
            int pointCount = pointCountByDevice.getOrDefault(devicePlan.deviceKey(), 0);
            stats.addDeploymentPreview(new DeploymentPreviewItem(
                    devicePlan.firstRow().sourceFile(),
                    devicePlan.firstRow().deviceAlias(),
                    devicePlan.devicePath(),
                    devicePlan.systemName(),
                    templateName,
                    pointCount,
                    action));
        }

        if (!stats.valid() || dryRun) {
            return stats.toSummary();
        }

        List<NavNode> allNavNodes = new ArrayList<>(navNodeMapper.findAllIncludingDeleted());
        for (NavNode activeNode : navNodes) {
            if (allNavNodes.stream().noneMatch(node -> Objects.equals(node.getNodeId(), activeNode.getNodeId()))) {
                allNavNodes.add(activeNode);
            }
        }
        Map<String, Long> navigationNodeIds = ensureNavigationPaths(
                devicePlans.values(), navNodes, allNavNodes);
        Map<String, Long> deviceIds = persistDevices(devicePlans.values(), navigationNodeIds);
        persistPointTemplateBackfills(pointPlans.values());
        persistPoints(pointPlans.values(), deviceIds);
        persistAlarmRules(alarmState.plans());
        persistAlarmCodeLinks(pointPlans.values(), deviceIds, alarmState.rowAlarmNames());
        stats.committed();
        registerRedisSyncAfterCommit();
        return stats.toSummary();
    }

    private List<DeploymentCsvData> parseFiles(List<MultipartFile> files, ImportStats stats) {
        List<DeploymentCsvData> csvFiles = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            stats.error("EMPTY_INPUT", "No CSV files were provided", null, null);
            return csvFiles;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                stats.error("EMPTY_FILE", "An uploaded file is empty", null, null);
                continue;
            }
            String fileName = safeFileName(file.getOriginalFilename());
            try {
                byte[] bytes = file.getBytes();
                if (fileName.toLowerCase().endsWith(".zip")) {
                    parseZip(fileName, bytes, csvFiles, stats);
                } else if (fileName.toLowerCase().endsWith(".csv")) {
                    csvFiles.add(csvParser.parse(fileName, new ByteArrayInputStream(bytes)));
                } else {
                    stats.error("UNSUPPORTED_FILE", "Only CSV or ZIP files are supported", fileName, null);
                }
            } catch (IOException | IllegalArgumentException exception) {
                stats.error("INVALID_FILE", exception.getMessage(), fileName, null);
            }
        }
        return csvFiles;
    }

    private void parseZip(String fileName, byte[] bytes, List<DeploymentCsvData> target, ImportStats stats)
            throws IOException {
        boolean foundCsv = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".csv")) {
                    continue;
                }
                foundCsv = true;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                String entryName = safeFileName(entry.getName());
                try {
                    target.add(csvParser.parse(entryName, new ByteArrayInputStream(output.toByteArray())));
                } catch (IllegalArgumentException exception) {
                    stats.error("INVALID_FILE", exception.getMessage(), fileName + "/" + entryName, null);
                }
            }
        }
        if (!foundCsv) {
            stats.error("EMPTY_ARCHIVE", "ZIP archive contains no CSV files", fileName, null);
        }
    }

    private Map<String, DeviceTemplate> indexTemplatesByN4Name(List<DeviceTemplate> templates) {
        Map<String, DeviceTemplate> result = new LinkedHashMap<>();
        for (DeviceTemplate template : templates) {
            if (template.getN4Name() != null && !template.getN4Name().isBlank()) {
                result.putIfAbsent(template.getN4Name(), template);
            }
        }
        return result;
    }

    private List<PointDeployment> findExistingPointDeployments(List<DeploymentCsvRow> rows) {
        List<String> addresses = rows.stream()
                .map(DeploymentCsvRow::mqttPath)
                .filter(address -> address != null && !address.isBlank())
                .distinct()
                .toList();
        if (addresses.isEmpty()) {
            return List.of();
        }

        List<PointDeployment> result = new ArrayList<>();
        for (int start = 0; start < addresses.size(); start += POINT_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + POINT_LOOKUP_BATCH_SIZE, addresses.size());
            result.addAll(pointDeploymentMapper.findAllByN4Addresses(addresses.subList(start, end)));
        }
        return result;
    }

    private Map<String, PointTemplate> indexPointTemplates(List<PointTemplate> points) {
        Map<String, PointTemplate> result = new HashMap<>();
        for (PointTemplate point : points) {
            result.put(pointKey(point.getDeviceTmplId(), point.getPointName()), point);
        }
        return result;
    }

    private Map<String, DeviceDeployment> indexDeployments(List<DeviceDeployment> deployments) {
        Map<String, DeviceDeployment> result = new HashMap<>();
        for (DeviceDeployment deployment : deployments) {
            result.putIfAbsent(deviceKey(deployment.getSystemName(), deployment.getDeviceDeployName()), deployment);
        }
        return result;
    }

    private boolean isActiveDeployment(DeviceDeployment deployment, Long activeNavNodeId) {
        return deployment != null
                && !Boolean.TRUE.equals(deployment.getIsDeleted())
                && activeNavNodeId != null
                && Objects.equals(activeNavNodeId, deployment.getNavNodeId());
    }

    private Map<String, PointDeployment> indexPointDeployments(List<PointDeployment> points) {
        Map<String, PointDeployment> result = new HashMap<>();
        for (PointDeployment point : points) {
            if (point.getN4Address() != null) {
                result.putIfAbsent(point.getN4Address(), point);
            }
        }
        return result;
    }

    private Long resolveNavigationNodeId(String systemName, List<NavNode> navNodes) {
        List<String> path = navigationPath(systemName);
        if (path.size() < 2) {
            return null;
        }

        List<NavNode> candidates = navNodes.stream()
                .filter(node -> path.getFirst().equals(trim(node.getNodeName())))
                .toList();
        for (int index = 1; index < path.size() && !candidates.isEmpty(); index++) {
            String segment = path.get(index);
            List<NavNode> parents = candidates;
            candidates = navNodes.stream()
                    .filter(node -> parents.stream().anyMatch(parent ->
                            java.util.Objects.equals(parent.getNodeId(), node.getParentId())
                                    && segment.equals(trim(node.getNodeName()))))
                    .toList();
        }

        return candidates.stream()
                .map(NavNode::getNodeId)
                .findFirst()
                .orElse(null);
    }

    private boolean canEnsureNavigationPath(String systemName, List<NavNode> navNodes) {
        List<String> path = navigationPath(systemName);
        if (path.size() < 2) {
            return false;
        }
        NavigationCursor cursor = findNavigationCursor(path, navNodes);
        if (cursor == null) {
            return false;
        }
        return cursor.node().getNodeLevel() + path.size() - cursor.nextSegmentIndex()
                <= resolveNavigationMaxLevel(cursor.node(), navNodes);
    }

    private Map<String, Long> ensureNavigationPaths(
            Iterable<DevicePlan> plans,
            List<NavNode> navNodes,
            List<NavNode> allNavNodes) {
        Map<String, Long> nodeIds = new HashMap<>();
        for (DevicePlan plan : plans) {
            String systemName = plan.systemName();
            if (nodeIds.containsKey(systemName)) {
                continue;
            }
            Long existingNodeId = resolveNavigationNodeId(systemName, navNodes);
            if (existingNodeId != null) {
                nodeIds.put(systemName, existingNodeId);
                continue;
            }

            List<String> path = navigationPath(systemName);
            NavigationCursor cursor = findNavigationCursor(path, navNodes);
            if (cursor == null
                    || cursor.node().getNodeLevel() + path.size() - cursor.nextSegmentIndex()
                    > resolveNavigationMaxLevel(cursor.node(), navNodes)) {
                continue;
            }

            NavNode parent = cursor.node();
            for (int index = cursor.nextSegmentIndex(); index < path.size(); index++) {
                String segment = path.get(index);
                NavNode child = findActiveChild(parent.getNodeId(), segment, navNodes);
                if (child == null) {
                    child = findDeletedChild(parent.getNodeId(), segment, allNavNodes);
                    if (child == null) {
                        child = new NavNode();
                        child.setParentId(parent.getNodeId());
                        child.setNodeName(segment);
                        child.setNodeLevel(parent.getNodeLevel() + 1);
                        child.setNodeType("group");
                        child.setSortOrder(nextNavigationSortOrder(parent.getNodeId(), navNodes));
                        child.setIsDeleted(false);
                        navNodeMapper.insert(child);
                        allNavNodes.add(child);
                    } else {
                        child.setParentId(parent.getNodeId());
                        child.setNodeName(segment);
                        child.setNodeLevel(parent.getNodeLevel() + 1);
                        child.setNodeType("group");
                        child.setIsDeleted(false);
                        navNodeMapper.restore(child);
                    }
                    navNodes.add(child);
                }
                parent = child;
            }
            nodeIds.put(systemName, parent.getNodeId());
        }
        return nodeIds;
    }

    private NavigationCursor findNavigationCursor(List<String> path, List<NavNode> navNodes) {
        List<NavNode> firstSegmentNodes = navNodes.stream()
                .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                .filter(node -> path.getFirst().equals(trim(node.getNodeName())))
                .toList();
        if (!firstSegmentNodes.isEmpty()) {
            return new NavigationCursor(firstSegmentNodes.getFirst(), 1);
        }
        return navNodes.stream()
                .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                .filter(node -> node.getNodeLevel() != null && node.getNodeLevel() == 1)
                .filter(node -> path.getFirst().startsWith(trim(node.getNodeName()) + "_"))
                .max(Comparator.comparingInt(node -> trim(node.getNodeName()).length()))
                .map(node -> new NavigationCursor(node, 0))
                .orElse(null);
    }

    private NavNode findActiveChild(Long parentId, String nodeName, List<NavNode> navNodes) {
        return navNodes.stream()
                .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                .filter(node -> Objects.equals(parentId, node.getParentId()))
                .filter(node -> nodeName.equals(trim(node.getNodeName())))
                .findFirst()
                .orElse(null);
    }

    private NavNode findDeletedChild(Long parentId, String nodeName, List<NavNode> navNodes) {
        return navNodes.stream()
                .filter(node -> Boolean.TRUE.equals(node.getIsDeleted()))
                .filter(node -> Objects.equals(parentId, node.getParentId()))
                .filter(node -> nodeName.equals(trim(node.getNodeName())))
                .max(Comparator.comparing(
                        NavNode::getUpdateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private int resolveNavigationMaxLevel(NavNode node, List<NavNode> navNodes) {
        NavNode current = node;
        while (current.getParentId() != null) {
            Long parentId = current.getParentId();
            current = navNodes.stream()
                    .filter(candidate -> Objects.equals(parentId, candidate.getNodeId()))
                    .findFirst()
                    .orElse(current);
            if (Objects.equals(current.getNodeId(), parentId)) {
                continue;
            }
            break;
        }
        return current.getMaxLevel() == null ? DEFAULT_NAV_MAX_LEVEL : current.getMaxLevel();
    }

    private int nextNavigationSortOrder(Long parentId, List<NavNode> navNodes) {
        return navNodes.stream()
                .filter(node -> Objects.equals(parentId, node.getParentId()))
                .map(NavNode::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private List<String> navigationPath(String systemName) {
        return Arrays.stream(systemName.split("/"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private String navigationGroupName(String systemName) {
        List<String> path = navigationPath(systemName);
        return path.isEmpty() ? null : path.getFirst();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Long> persistDevices(
            Iterable<DevicePlan> plans,
            Map<String, Long> navigationNodeIds) {
        Map<String, Long> ids = new HashMap<>();
        for (DevicePlan plan : plans) {
            Long navNodeId = navigationNodeIds.getOrDefault(plan.systemName(), plan.navNodeId());
            DeviceDeployment deployment = plan.existingDeployment();
            if (deployment == null) {
                deployment = buildDeployment(plan, navNodeId);
                deviceDeploymentMapper.insert(deployment);
            } else {
                if (Boolean.TRUE.equals(deployment.getIsDeleted())) {
                    deviceDeploymentMapper.restore(deployment.getDeviceDeployId());
                }
                if (navNodeId != null && !navNodeId.equals(deployment.getNavNodeId())) {
                    deviceDeploymentMapper.updateNode(
                            deployment.getDeviceDeployId(),
                            navNodeId,
                            deployment.getDeviceDeployName(),
                            deployment.getDeviceAlias(),
                            plan.firstRow().deviceN4Address());
                }
            }
            ids.put(plan.deviceKey(), deployment.getDeviceDeployId());
        }
        return ids;
    }

    private DeviceDeployment buildDeployment(DevicePlan plan, Long navNodeId) {
        DeploymentCsvRow row = plan.firstRow();
        DeviceDeployment deployment = new DeviceDeployment();
        deployment.setDeviceTmplId(plan.template().getDeviceTmplId());
        deployment.setNavNodeId(navNodeId);
        deployment.setSystemName(row.systemName());
        deployment.setDeviceDeployName(row.devicePath());
        deployment.setDeviceAlias(row.deviceAlias());
        deployment.setN4Address(row.deviceN4Address());
        deployment.setStatus(1);
        deployment.setIsDeleted(false);
        return deployment;
    }

    private void persistPointTemplateBackfills(Iterable<PointPlan> plans) {
        Set<Long> updated = new HashSet<>();
        for (PointPlan plan : plans) {
            PointTemplate point = plan.pointTemplate();
            if (plan.needsTemplateBackfill() && updated.add(point.getPointTmplId())) {
                pointTemplateMapper.updateTypeAndUnitIfEmpty(point);
            }
        }
    }

    private void persistPoints(Iterable<PointPlan> plans, Map<String, Long> deviceIds) {
        List<PointDeployment> pending = new ArrayList<>();
        for (PointPlan plan : plans) {
            DeploymentCsvRow row = plan.row();
            Long deviceId = deviceIds.get(plan.devicePlan().deviceKey());
            PointDeployment point = buildPoint(row, plan.pointTemplate(), deviceId);
            if (plan.existingPoint() == null) {
                pending.add(point);
                if (pending.size() >= POINT_BATCH_SIZE) {
                    pointDeploymentMapper.insertBatch(pending);
                    pending.clear();
                }
            } else {
                point.setPointDeployId(plan.existingPoint().getPointDeployId());
                if (Boolean.TRUE.equals(plan.existingPoint().getIsDeleted())) {
                    pointDeploymentMapper.restore(point.getPointDeployId());
                }
                if (Boolean.TRUE.equals(plan.existingPoint().getIsDeleted())
                        || hasImportedMetadataChanged(plan.existingPoint(), point)) {
                    pointDeploymentMapper.updateImportedMetadata(point);
                }
            }
        }
        if (!pending.isEmpty()) {
            pointDeploymentMapper.insertBatch(pending);
        }
    }

    private boolean hasImportedMetadataChanged(PointDeployment existing, PointDeployment imported) {
        return !Objects.equals(existing.getPointTmplId(), imported.getPointTmplId())
                || !Objects.equals(existing.getDeviceDeployId(), imported.getDeviceDeployId())
                || !Objects.equals(existing.getPointName(), imported.getPointName())
                || !Objects.equals(existing.getN4Address(), imported.getN4Address())
                || !Objects.equals(existing.getPointCode(), imported.getPointCode())
                || !Objects.equals(existing.getDataType(), imported.getDataType())
                || !Objects.equals(existing.getUnit(), imported.getUnit())
                || !Objects.equals(existing.getDisplayPrecision(), imported.getDisplayPrecision())
                || !Objects.equals(existing.getAllPath(), imported.getAllPath())
                || !Objects.equals(existing.getType(), imported.getType())
                || !Objects.equals(existing.getConversionScale(), imported.getConversionScale())
                || !Objects.equals(existing.getConversionOffset(), imported.getConversionOffset())
                || !Objects.equals(existing.getPollFrequency(), imported.getPollFrequency())
                || !Objects.equals(existing.getAddressFormat(), imported.getAddressFormat())
                || !Objects.equals(existing.getModbusAddress(), imported.getModbusAddress())
                || !Objects.equals(existing.getNumberOfBits(), imported.getNumberOfBits());
    }

    private PointDeployment buildPoint(DeploymentCsvRow row, PointTemplate pointTemplate, Long deviceId) {
        PointDeployment point = new PointDeployment();
        point.setPointTmplId(pointTemplate.getPointTmplId());
        point.setDeviceDeployId(deviceId);
        point.setPointName(row.pointName());
        point.setN4Address(row.mqttPath());
        point.setAllPath(row.allPath());
        point.setType(pointTemplate.getType() == null ? row.type() : pointTemplate.getType());
        point.setUnit(pointTemplate.getUnit() == null ? normalizeUnit(row.unit()) : pointTemplate.getUnit());
        point.setDataType(null);
        point.setConversionScale(toDouble(row.field("conversionscale")));
        point.setConversionOffset(toDouble(row.field("conversionoffset")));
        point.setPollFrequency(row.pollFrequency());
        point.setAddressFormat(row.addressFormat());
        point.setModbusAddress(row.modbusAddress());
        point.setNumberOfBits(toInteger(row.numberOfBits()));
        point.setDisplayPrecision(0);
        point.setIsDeleted(false);
        return point;
    }

    private AlarmState buildAlarmState(Iterable<PointPlan> pointPlans, ImportStats stats) {
        List<AlarmRuleEnumEntity> existingEnumRules = alarmRuleEnumMapper.findAll();
        List<AlarmRuleNumericEntity> existingNumericRules = alarmRuleNumericMapper.findAll();
        Map<String, String> alarmNameBySignature = new HashMap<>();
        Set<String> usedAlarmNames = new HashSet<>();
        Map<String, Integer> nextSequence = new HashMap<>();
        indexExistingEnumRules(existingEnumRules, alarmNameBySignature, usedAlarmNames, nextSequence);
        indexExistingNumericRules(existingNumericRules, alarmNameBySignature, usedAlarmNames, nextSequence);

        Map<String, String> rowAlarmNames = new HashMap<>();
        List<AlarmPlan> plans = new ArrayList<>();
        Map<String, AlarmPlan> newPlansBySignature = new HashMap<>();
        for (PointPlan pointPlan : pointPlans) {
            DeploymentCsvRow row = pointPlan.row();
            AlarmCandidate candidate;
            try {
                candidate = buildAlarmCandidate(row);
            } catch (IllegalArgumentException exception) {
                stats.error("INVALID_ALARM_RULE", exception.getMessage(), row.sourceFile(), row.rowNumber());
                continue;
            }
            if (candidate == null) {
                continue;
            }
            String key = row.pointName() + KEY_SEPARATOR + candidate.kind() + KEY_SEPARATOR + candidate.signature();
            String alarmName = alarmNameBySignature.get(key);
            if (alarmName == null) {
                AlarmPlan existingPlan = newPlansBySignature.get(key);
                if (existingPlan != null) {
                    alarmName = existingPlan.alarmName();
                } else {
                    alarmName = allocateAlarmName(row.pointName(), usedAlarmNames, nextSequence);
                    AlarmPlan newPlan = buildAlarmPlan(candidate, alarmName);
                    newPlansBySignature.put(key, newPlan);
                    plans.add(newPlan);
                    alarmNameBySignature.put(key, alarmName);
                    if ("enum".equals(candidate.kind())) {
                        for (int index = 0; index < newPlan.enumRows().size(); index++) {
                            stats.enumRuleCreated();
                        }
                    } else {
                        stats.numericRuleCreated();
                    }
                }
            }
            rowAlarmNames.put(rowKey(row), alarmName);
        }
        return new AlarmState(plans, rowAlarmNames);
    }

    private void indexExistingEnumRules(
            List<AlarmRuleEnumEntity> rules,
            Map<String, String> signatures,
            Set<String> usedNames,
            Map<String, Integer> nextSequence) {
        Map<String, List<AlarmRuleEnumEntity>> grouped = new TreeMap<>();
        for (AlarmRuleEnumEntity rule : rules) {
            if (rule.getAlarmName() != null) {
                grouped.computeIfAbsent(rule.getAlarmName(), ignored -> new ArrayList<>()).add(rule);
                usedNames.add(rule.getAlarmName());
                updateSequence(rule.getAlarmName(), nextSequence);
            }
        }
        for (Map.Entry<String, List<AlarmRuleEnumEntity>> entry : grouped.entrySet()) {
            String pointName = pointNameFromAlarmName(entry.getKey());
            if (pointName == null) {
                continue;
            }
            List<EnumValue> values = entry.getValue().stream()
                    .map(rule -> new EnumValue(
                            rule.getEnumValue(),
                            rule.getEnumMeaning(),
                            rule.getSeverity(),
                            rule.getMessage()))
                    .sorted(Comparator.comparing(value -> nullToEmpty(value.value())))
                    .toList();
            String signature = enumSignature(entry.getValue().getFirst().getTimeDelay(), values);
            signatures.putIfAbsent(pointName + KEY_SEPARATOR + "enum" + KEY_SEPARATOR + signature, entry.getKey());
        }
    }

    private void indexExistingNumericRules(
            List<AlarmRuleNumericEntity> rules,
            Map<String, String> signatures,
            Set<String> usedNames,
            Map<String, Integer> nextSequence) {
        for (AlarmRuleNumericEntity rule : rules) {
            if (rule.getAlarmName() == null) {
                continue;
            }
            usedNames.add(rule.getAlarmName());
            updateSequence(rule.getAlarmName(), nextSequence);
            String pointName = pointNameFromAlarmName(rule.getAlarmName());
            if (pointName == null) {
                continue;
            }
            String signature = numericSignature(new NumericValues(
                    rule.getDeadband(),
                    rule.getHighHighValue(), rule.getHighHighSeverity(), rule.getHighHighMessage(),
                    rule.getHighValue(), rule.getHighSeverity(), rule.getHighMessage(),
                    rule.getLowValue(), rule.getLowSeverity(), rule.getLowMessage(),
                    rule.getLowLowValue(), rule.getLowLowSeverity(), rule.getLowLowMessage()),
                    rule.getTimeDelay());
            signatures.putIfAbsent(pointName + KEY_SEPARATOR + "numeric" + KEY_SEPARATOR + signature, rule.getAlarmName());
        }
    }

    private AlarmCandidate buildAlarmCandidate(DeploymentCsvRow row) {
        String menu = row.menu();
        int timeDelay = defaultInteger(row.field("alarmtimedelay"), 0);
        String alarmClass = row.field("alarmclass");
        if (menu != null) {
            List<EnumValue> values = parseMenu(menu, alarmClass);
            if (values.isEmpty()) {
                return null;
            }
            return new AlarmCandidate(
                    "enum",
                    "enum",
                    timeDelay,
                    values,
                    null,
                    enumSignature(timeDelay, values));
        }

        if ("registerbit".equalsIgnoreCase(nullToEmpty(row.sourceType()))) {
            String trueText = row.trueText();
            String falseText = row.falseText();
            if (trueText == null && falseText == null) {
                return null;
            }
            String alarmValue = row.field("booleanchangeofstatealgorithmalarmvalue");
            String offnormalText = row.field("alarmtooffnormaltext");
            String alarmText = "FALSE".equalsIgnoreCase(nullToEmpty(alarmValue)) ? falseText : trueText;
            List<EnumValue> values = new ArrayList<>();
            if (trueText != null) {
                values.add(new EnumValue(trueText, trueText,
                        trueText.equals(alarmText) ? alarmClass : null,
                        trueText.equals(alarmText) ? offnormalText : null));
            }
            if (falseText != null && !falseText.equals(trueText)) {
                values.add(new EnumValue(falseText, falseText,
                        falseText.equals(alarmText) ? alarmClass : null,
                        falseText.equals(alarmText) ? offnormalText : null));
            }
            return new AlarmCandidate(
                    "enum",
                    "enum_boolean",
                    timeDelay,
                    values,
                    null,
                    enumSignature(timeDelay, values));
        }

        if ("numeric".equalsIgnoreCase(nullToEmpty(row.sourceType()))) {
            String highHighValue = row.field("outofrangefaultalgorithmhighlimit");
            String highValue = row.field("outofrangealgorithmhighlimit");
            String lowValue = row.field("outofrangealgorithmlowlimit");
            String lowLowValue = row.field("outofrangefaultalgorithmlowlimit");
            NumericValues values = new NumericValues(
                    row.field("outofrangefaultalgorithmdeadband"),
                    highHighValue,
                    highHighValue == null ? null : alarmClass,
                    row.field("outofrangefaultalgorithmhighlimittext"),
                    highValue, highValue == null ? null : alarmClass,
                    row.field("outofrangealgorithmhighlimittext"),
                    lowValue, lowValue == null ? null : alarmClass,
                    row.field("outofrangealgorithmlowlimittext"),
                    lowLowValue, lowLowValue == null ? null : alarmClass,
                    row.field("outofrangefaultalgorithmlowlimittext"));
            if (values.hasNoThreshold()) {
                return null;
            }
            return new AlarmCandidate(
                    "numeric",
                    "numeric",
                    timeDelay,
                    List.of(),
                    values,
                    numericSignature(values, timeDelay));
        }
        return null;
    }

    private List<EnumValue> parseMenu(String menu, String alarmClass) {
        List<EnumValue> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String entry : menu.split(";")) {
            String[] parts = entry.split(":", -1);
            if (parts.length != 3) {
                continue;
            }
            String value = blankToNull(parts[0]);
            String meaning = blankToNull(parts[1]);
            String isAlarm = blankToNull(parts[2]);
            if (value == null || !seen.add(value)) {
                continue;
            }
            boolean alarm = "1".equals(isAlarm);
            values.add(new EnumValue(value, meaning, alarm ? alarmClass : null, alarm ? meaning : null));
        }
        return values;
    }

    private AlarmPlan buildAlarmPlan(AlarmCandidate candidate, String alarmName) {
        if ("enum".equals(candidate.kind())) {
            List<AlarmRuleEnumEntity> rows = new ArrayList<>();
            for (EnumValue value : candidate.enumValues()) {
                AlarmRuleEnumEntity entity = new AlarmRuleEnumEntity();
                entity.setAlarmName(alarmName);
                entity.setPointType(candidate.pointType());
                entity.setTimeDelay(candidate.timeDelay());
                entity.setEnumValue(value.value());
                entity.setEnumMeaning(value.meaning());
                entity.setSeverity(value.severity());
                entity.setMessage(value.message());
                rows.add(entity);
            }
            return new AlarmPlan(alarmName, "enum", rows, null);
        }
        AlarmRuleNumericEntity entity = new AlarmRuleNumericEntity();
        NumericValues values = candidate.numericValues();
        entity.setAlarmName(alarmName);
        entity.setPointType(candidate.pointType());
        entity.setTimeDelay(candidate.timeDelay());
        entity.setDeadband(values.deadband());
        entity.setHighHighValue(values.highHighValue());
        entity.setHighHighSeverity(values.highHighSeverity());
        entity.setHighHighMessage(values.highHighMessage());
        entity.setHighValue(values.highValue());
        entity.setHighSeverity(values.highSeverity());
        entity.setHighMessage(values.highMessage());
        entity.setLowValue(values.lowValue());
        entity.setLowSeverity(values.lowSeverity());
        entity.setLowMessage(values.lowMessage());
        entity.setLowLowValue(values.lowLowValue());
        entity.setLowLowSeverity(values.lowLowSeverity());
        entity.setLowLowMessage(values.lowLowMessage());
        return new AlarmPlan(alarmName, "numeric", List.of(), entity);
    }

    private void persistAlarmRules(List<AlarmPlan> plans) {
        for (AlarmPlan plan : plans) {
            if ("enum".equals(plan.kind())) {
                for (AlarmRuleEnumEntity row : plan.enumRows()) {
                    alarmRuleEnumMapper.insert(row);
                }
            } else {
                alarmRuleNumericMapper.insert(plan.numericRow());
            }
        }
    }

    private void persistAlarmCodeLinks(
            Iterable<PointPlan> pointPlans,
            Map<String, Long> deviceIds,
            Map<String, String> rowAlarmNames) {
        Map<Long, String> deviceTemplateAlarmCodes = new LinkedHashMap<>();
        Map<Long, String> pointTemplateAlarmCodes = new LinkedHashMap<>();
        Map<Long, String> deploymentAlarmCodes = new LinkedHashMap<>();
        for (PointPlan plan : pointPlans) {
            String alarmName = rowAlarmNames.get(rowKey(plan.row()));
            if (alarmName != null) {
                pointTemplateAlarmCodes.putIfAbsent(plan.pointTemplate().getPointTmplId(), alarmName);
                if (plan.row().pointName().toLowerCase().startsWith("communication_")) {
                    deviceTemplateAlarmCodes.putIfAbsent(plan.devicePlan().template().getDeviceTmplId(), alarmName);
                    deploymentAlarmCodes.putIfAbsent(
                            deviceIds.get(plan.devicePlan().deviceKey()), alarmName);
                }
            }
            if (alarmName != null) {
                PointDeployment existingPoint = plan.existingPoint();
                if (existingPoint == null || !Objects.equals(existingPoint.getAlarmCode(), alarmName)) {
                    pointDeploymentMapper.updateAlarmCodeByN4Address(plan.row().mqttPath(), alarmName);
                }
            }
        }
        for (Map.Entry<Long, String> entry : deviceTemplateAlarmCodes.entrySet()) {
            deviceTemplateMapper.updateAlarmCode(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, String> entry : pointTemplateAlarmCodes.entrySet()) {
            pointTemplateMapper.updateAlarmCode(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, String> entry : deploymentAlarmCodes.entrySet()) {
            deviceDeploymentMapper.updateAlarmCode(entry.getKey(), entry.getValue());
        }
    }

    private void registerRedisSyncAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleRedisSync();
                }
            });
        } else {
            scheduleRedisSync();
        }
    }

    private void scheduleRedisSync() {
        boolean submit;
        synchronized (redisSyncMonitor) {
            redisSyncPending = true;
            submit = !redisSyncRunning;
            redisSyncRunning = true;
        }
        if (!submit) {
            return;
        }
        try {
            redisSyncExecutor.execute(this::drainRedisSyncRequests);
        } catch (RuntimeException exception) {
            synchronized (redisSyncMonitor) {
                redisSyncRunning = false;
            }
            log.error("Failed to schedule imported metadata Redis sync", exception);
        }
    }

    private void drainRedisSyncRequests() {
        while (true) {
            synchronized (redisSyncMonitor) {
                if (!redisSyncPending) {
                    redisSyncRunning = false;
                    return;
                }
                redisSyncPending = false;
            }
            try {
                alarmRuleSyncService.syncAllRulesToRedis();
                alarmRuleSyncService.syncPointRuleMapToRedis();
            } catch (RuntimeException exception) {
                log.error("Failed to sync imported metadata to Redis", exception);
            }
        }
    }

    @PreDestroy
    void shutdownRedisSyncExecutor() {
        redisSyncExecutor.shutdownNow();
    }

    private String allocateAlarmName(String pointName, Set<String> usedNames, Map<String, Integer> nextSequence) {
        int sequence = nextSequence.getOrDefault(pointName, -1) + 1;
        String candidate;
        do {
            candidate = String.format("%s_%02d", pointName, sequence++);
        } while (!usedNames.add(candidate));
        nextSequence.put(pointName, sequence - 1);
        return candidate;
    }

    private void updateSequence(String alarmName, Map<String, Integer> nextSequence) {
        int separator = alarmName.lastIndexOf('_');
        if (separator < 0) {
            return;
        }
        try {
            int sequence = Integer.parseInt(alarmName.substring(separator + 1));
            String pointName = alarmName.substring(0, separator);
            nextSequence.merge(pointName, sequence, Math::max);
        } catch (NumberFormatException ignored) {
            // Existing custom alarm names do not participate in sequence allocation.
        }
    }

    private String pointNameFromAlarmName(String alarmName) {
        int separator = alarmName.lastIndexOf('_');
        if (separator <= 0 || separator == alarmName.length() - 1) {
            return null;
        }
        try {
            Integer.parseInt(alarmName.substring(separator + 1));
            return alarmName.substring(0, separator);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String enumSignature(Integer timeDelay, List<EnumValue> values) {
        return nullToEmpty(String.valueOf(timeDelay)) + "|"
                + values.stream()
                .sorted(Comparator.comparing(value -> nullToEmpty(value.value())))
                .map(value -> joinValues(value.value(), value.meaning(), value.severity(), value.message()))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private String numericSignature(NumericValues values, Integer timeDelay) {
        return nullToEmpty(String.valueOf(timeDelay)) + "|"
                + joinValues(values.deadband(), values.highHighValue(), values.highHighMessage(),
                values.lowLowValue(), values.lowLowMessage(), values.highValue(), values.highMessage(),
                values.lowValue(), values.lowMessage(), values.highHighSeverity(), values.highSeverity(),
                values.lowSeverity(), values.lowLowSeverity());
    }

    private String joinValues(String... values) {
        return Arrays.stream(values).map(this::nullToEmpty).map(value -> value.replace("|", "\\|")).reduce(
                (left, right) -> left + "|" + right).orElse("");
    }

    private int defaultInteger(String value, int defaultValue) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return defaultValue;
        }
        return Integer.parseInt(normalized);
    }

    private Integer toInteger(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : Integer.valueOf(normalized);
    }

    private Double toDouble(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : Double.valueOf(normalized);
    }

    private String normalizeUnit(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        boolean malformedDegree = normalized.contains("?")
                || normalized.contains("\uFFFD")
                || normalized.contains("\u00B0");
        if (malformedDegree && normalized.endsWith("C")) {
            return "\u00B0C";
        }
        return normalized;
    }

    private String required(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("CSV field is required: " + field);
        }
        return normalized;
    }

    private String deviceKey(String systemName, String deviceName) {
        return nullToEmpty(systemName) + KEY_SEPARATOR + deviceName;
    }

    private String pointKey(Long templateId, String pointName) {
        return String.valueOf(templateId) + KEY_SEPARATOR + pointName;
    }

    private String rowKey(DeploymentCsvRow row) {
        return deviceKey(row.systemName(), row.devicePath()) + KEY_SEPARATOR + row.pointName();
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.csv";
        }
        int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(separator + 1);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record DevicePlan(
            String devicePath,
            DeploymentCsvRow firstRow,
            DeviceTemplate template,
            DeviceDeployment existingDeployment,
            Long navNodeId,
            String deviceKey) {

        private String systemName() {
            return firstRow.systemName();
        }
    }

    private record NavigationCursor(NavNode node, int nextSegmentIndex) {
    }

    private record PointPlan(
            DeploymentCsvRow row,
            DevicePlan devicePlan,
            PointTemplate pointTemplate,
            PointDeployment existingPoint,
            boolean needsTemplateBackfill) {
    }

    private record AlarmState(List<AlarmPlan> plans, Map<String, String> rowAlarmNames) {
    }

    private record AlarmPlan(
            String alarmName,
            String kind,
            List<AlarmRuleEnumEntity> enumRows,
            AlarmRuleNumericEntity numericRow) {
    }

    private record AlarmCandidate(
            String kind,
            String pointType,
            int timeDelay,
            List<EnumValue> enumValues,
            NumericValues numericValues,
            String signature) {
    }

    private record EnumValue(String value, String meaning, String severity, String message) {
    }

    private record NumericValues(
            String deadband,
            String highHighValue,
            String highHighSeverity,
            String highHighMessage,
            String highValue,
            String highSeverity,
            String highMessage,
            String lowValue,
            String lowSeverity,
            String lowMessage,
            String lowLowValue,
            String lowLowSeverity,
            String lowLowMessage) {

        private boolean hasNoThreshold() {
            return highHighValue == null && highValue == null && lowValue == null && lowLowValue == null;
        }
    }
}
