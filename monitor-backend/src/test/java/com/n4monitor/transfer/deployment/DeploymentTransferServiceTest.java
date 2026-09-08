package com.n4monitor.transfer.deployment;

import com.n4monitor.alarm.mapper.AlarmRuleEnumMapper;
import com.n4monitor.alarm.mapper.AlarmRuleNumericMapper;
import com.n4monitor.alarm.service.AlarmRuleSyncService;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import com.n4monitor.transfer.ImportSummary;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class DeploymentTransferServiceTest {

    @Test
    void importedPointDeploymentKeepsCanonicalTypeAndLeavesDataTypeEmpty() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);
        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());
        doAnswer(invocation -> {
            DeviceDeployment deployment = invocation.getArgument(0);
            deployment.setDeviceDeployId(20L);
            return 1;
        }).when(deviceDeploymentMapper).insert(any(DeviceDeployment.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,dataType,unit",
                "A1_MVP_A1_MVP_A1_00_Collector_00,Temperature,numeric,all/path,slot:/A1/MVP/Collector,,C");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(pointDeploymentMapper).insertBatch(captor.capture());
        @SuppressWarnings("unchecked")
        List<PointDeployment> importedPoints = (List<PointDeployment>) captor.getValue();
        assertThat(importedPoints).hasSize(1);
        assertThat(importedPoints.getFirst().getType()).isEqualTo("numeric");
        assertThat(importedPoints.getFirst().getDataType()).isNull();
    }

    @Test
    void createsSeparateDeploymentsForTheSameDevicePathInDifferentSystems() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(navNodeMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        long[] nextDeviceId = {100L};
        doAnswer(invocation -> {
            DeviceDeployment deployment = invocation.getArgument(0);
            deployment.setDeviceDeployId(nextDeviceId[0]++);
            return 1;
        }).when(deviceDeploymentMapper).insert(any(DeviceDeployment.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String sharedDevicePath = "MVP_SHARED_00_Collector_00";
        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                sharedDevicePath + ",Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/" + sharedDevicePath + "/Temperature",
                sharedDevicePath + ",Temperature,numeric,all/path,"
                        + "A1_MVP_B1/MVP_B1/" + sharedDevicePath + "/Temperature");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        assertThat(summary.deviceDeploymentCreated()).isEqualTo(2);
        assertThat(summary.deviceDeploymentReused()).isZero();
        assertThat(summary.pointDeploymentCreated()).isEqualTo(2);

        var deviceCaptor = org.mockito.ArgumentCaptor.forClass(DeviceDeployment.class);
        verify(deviceDeploymentMapper, org.mockito.Mockito.times(2)).insert(deviceCaptor.capture());
        assertThat(deviceCaptor.getAllValues())
                .extracting(DeviceDeployment::getSystemName)
                .containsExactly("A1_MVP_A1/MVP_A1", "A1_MVP_B1/MVP_B1");

        var pointCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pointDeploymentMapper).insertBatch(pointCaptor.capture());
        @SuppressWarnings("unchecked")
        List<PointDeployment> importedPoints = (List<PointDeployment>) pointCaptor.getValue();
        assertThat(importedPoints)
                .extracting(PointDeployment::getDeviceDeployId)
                .containsExactly(100L, 101L);
    }

    @Test
    void keepsAlarmLinksSeparateForTheSameDevicePathInDifferentSystems() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Communication_status_1");
        pointTemplate.setType("enum");
        pointTemplate.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(navNodeMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        long[] nextDeviceId = {100L};
        doAnswer(invocation -> {
            DeviceDeployment deployment = invocation.getArgument(0);
            deployment.setDeviceDeployId(nextDeviceId[0]++);
            return 1;
        }).when(deviceDeploymentMapper).insert(any(DeviceDeployment.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String sharedDevicePath = "MVP_SHARED_00_Collector_00";
        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,trueText,falseText,alarmClass",
                sharedDevicePath + ",Communication_status_1,registerbit,all/path,"
                        + "A1_MVP_A1/MVP_A1/" + sharedDevicePath + "/Communication_status_1,"
                        + "Alarm A,Normal A,Major",
                sharedDevicePath + ",Communication_status_1,registerbit,all/path,"
                        + "A1_MVP_B1/MVP_B1/" + sharedDevicePath + "/Communication_status_1,"
                        + "Alarm B,Normal B,Major");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        var pointCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pointDeploymentMapper).insertBatch(pointCaptor.capture());
        @SuppressWarnings("unchecked")
        List<PointDeployment> importedPoints = (List<PointDeployment>) pointCaptor.getValue();
        assertThat(importedPoints)
                .extracting(PointDeployment::getAlarmCode)
                .containsExactly("Communication_status_1_00", "Communication_status_1_01");
        verify(deviceDeploymentMapper).updateAlarmCode(100L, "Communication_status_1_00");
        verify(deviceDeploymentMapper).updateAlarmCode(101L, "Communication_status_1_01");
    }

    @Test
    void nestedNavigationPathResolvesToTheMatchingLeafNode() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);
        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP");
        NavNode deviceGroup = new NavNode();
        deviceGroup.setNodeId(101L);
        deviceGroup.setParentId(100L);
        deviceGroup.setNodeName("A1_MVP_A1");
        NavNode leaf = new NavNode();
        leaf.setNodeId(102L);
        leaf.setParentId(101L);
        leaf.setNodeName("MVP_A1");

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of(root, deviceGroup, leaf));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());
        doAnswer(invocation -> {
            DeviceDeployment deployment = invocation.getArgument(0);
            deployment.setDeviceDeployId(20L);
            return 1;
        }).when(deviceDeploymentMapper).insert(any(DeviceDeployment.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,dataType",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/points/MqttNetwork/"
                        + "MVP_A1_00_Collector_00/Temperature,");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        assertThat(summary.issues())
                .noneMatch(issue -> "NAVIGATION_NODE_NOT_FOUND".equals(issue.code()));
        var captor = org.mockito.ArgumentCaptor.forClass(DeviceDeployment.class);
        verify(deviceDeploymentMapper).insert(captor.capture());
        assertThat(captor.getValue().getNavNodeId()).isEqualTo(102L);
    }

    @Test
    void countsUnmountedDeviceAsNewAndMountsExistingRecord() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setSystemName("A1_MVP_D1/MVP_D1");
        existingDevice.setDeviceDeployName("MVP_D1_DEMO_01_Collector_00");
        existingDevice.setIsDeleted(false);

        String n4Address = "A1_MVP_D1/MVP_D1/MVP_D1_DEMO_01_Collector_00/Temperature";
        PointDeployment existingPoint = new PointDeployment();
        existingPoint.setPointDeployId(30L);
        existingPoint.setPointTmplId(10L);
        existingPoint.setDeviceDeployId(20L);
        existingPoint.setPointName("Temperature");
        existingPoint.setN4Address(n4Address);
        existingPoint.setAllPath("all/path");
        existingPoint.setType("numeric");
        existingPoint.setDisplayPrecision(0);
        existingPoint.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP");
        root.setNodeLevel(1);
        root.setMaxLevel(3);
        root.setSortOrder(1);
        root.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(n4Address))).thenReturn(List.of(existingPoint));
        when(navNodeMapper.findAll()).thenReturn(List.of(root));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());
        doAnswer(invocation -> {
            NavNode node = invocation.getArgument(0);
            node.setNodeId("A1_MVP_D1".equals(node.getNodeName()) ? 101L : 102L);
            return null;
        }).when(navNodeMapper).insert(any(NavNode.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_D1_DEMO_01_Collector_00,Temperature,numeric,all/path," + n4Address);
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        assertThat(summary.deviceDeploymentCreated()).isEqualTo(1);
        assertThat(summary.deviceDeploymentReused()).isZero();
        var nodeCaptor = org.mockito.ArgumentCaptor.forClass(NavNode.class);
        verify(navNodeMapper, org.mockito.Mockito.times(2)).insert(nodeCaptor.capture());
        assertThat(nodeCaptor.getAllValues())
                .extracting(NavNode::getNodeName)
                .containsExactly("A1_MVP_D1", "MVP_D1");
        verify(deviceDeploymentMapper).updateNode(
                20L,
                102L,
                "MVP_D1_DEMO_01_Collector_00",
                null,
                "A1_MVP_D1/MVP_D1/MVP_D1_DEMO_01_Collector_00");
    }

    @Test
    void reusesDeviceAlreadyMountedInTheCurrentSystemPath() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setNavNodeId(101L);
        existingDevice.setSystemName("A1_MVP_A1/MVP_A1");
        existingDevice.setDeviceDeployName("MVP_A1_00_Collector_00");
        existingDevice.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP_A1");
        root.setNodeLevel(1);
        root.setMaxLevel(2);
        root.setIsDeleted(false);
        NavNode leaf = new NavNode();
        leaf.setNodeId(101L);
        leaf.setParentId(100L);
        leaf.setNodeName("MVP_A1");
        leaf.setNodeLevel(2);
        leaf.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllByN4Addresses(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of(root, leaf));
        when(navNodeMapper.findAllIncludingDeleted()).thenReturn(List.of(root, leaf));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = service.importFiles(List.of(file), true);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.deviceDeploymentCreated()).isZero();
        assertThat(summary.deviceDeploymentReused()).isEqualTo(1);
        assertThat(summary.deploymentPreviews()).hasSize(1);
        assertThat(summary.deploymentPreviews().get(0).deviceName()).isEqualTo("MVP_A1_Collector");
        assertThat(summary.deploymentPreviews().get(0).action()).isEqualTo("REUSE");
        assertThat(summary.deploymentPreviews().get(0).pointCount()).isEqualTo(1);
        verify(deviceDeploymentMapper, never()).insert(any(DeviceDeployment.class));
    }

    @Test
    void restoresDeletedNavigationPathInsteadOfCreatingDuplicates() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP");
        root.setNodeLevel(1);
        root.setMaxLevel(3);
        root.setIsDeleted(false);

        NavNode deletedGroup = new NavNode();
        deletedGroup.setNodeId(101L);
        deletedGroup.setParentId(100L);
        deletedGroup.setNodeName("A1_MVP_D1");
        deletedGroup.setNodeLevel(2);
        deletedGroup.setSortOrder(3);
        deletedGroup.setIsDeleted(true);

        NavNode deletedLeaf = new NavNode();
        deletedLeaf.setNodeId(102L);
        deletedLeaf.setParentId(101L);
        deletedLeaf.setNodeName("MVP_D1");
        deletedLeaf.setNodeLevel(3);
        deletedLeaf.setSortOrder(1);
        deletedLeaf.setIsDeleted(true);

        String n4Address = "A1_MVP_D1/MVP_D1/MVP_D1_DEMO_01_Collector_00/Temperature";
        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(n4Address))).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of(root));
        when(navNodeMapper.findAllIncludingDeleted()).thenReturn(List.of(root, deletedGroup, deletedLeaf));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());
        doAnswer(invocation -> {
            DeviceDeployment deployment = invocation.getArgument(0);
            deployment.setDeviceDeployId(20L);
            return 1;
        }).when(deviceDeploymentMapper).insert(any(DeviceDeployment.class));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_D1_DEMO_01_Collector_00,Temperature,numeric,all/path," + n4Address);
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        verify(navNodeMapper).restore(deletedGroup);
        verify(navNodeMapper).restore(deletedLeaf);
        verify(navNodeMapper, never()).insert(any(NavNode.class));
        var deviceCaptor = org.mockito.ArgumentCaptor.forClass(DeviceDeployment.class);
        verify(deviceDeploymentMapper).insert(deviceCaptor.capture());
        assertThat(deviceCaptor.getValue().getNavNodeId()).isEqualTo(102L);
    }

    @Test
    void previewReportsMissingNavigationGroupAsNewWithoutWriting() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP");
        root.setNodeLevel(1);
        root.setMaxLevel(3);
        root.setIsDeleted(false);

        String n4Address = "A1_MVP_D1/MVP_D1/MVP_D1_DEMO_01_Collector_00/Temperature";
        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(n4Address))).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of(root));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_D1_DEMO_01_Collector_00,Temperature,numeric,all/path," + n4Address);
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), true);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.dryRun()).isTrue();
        assertThat(summary.navigationGroupCreated()).isEqualTo(1);
        assertThat(summary.navigationGroupNames()).containsExactly("A1_MVP_D1");
        verify(navNodeMapper, never()).insert(any(NavNode.class));
        verify(navNodeMapper, never()).restore(any(NavNode.class));
    }

    @Test
    void skipsDatabaseWriteWhenImportedPointMetadataIsUnchanged() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setUnit("C");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setSystemName("A1_MVP_A1/MVP_A1");
        existingDevice.setDeviceDeployName("MVP_A1_00_Collector_00");
        existingDevice.setIsDeleted(false);

        PointDeployment existingPoint = new PointDeployment();
        existingPoint.setPointDeployId(30L);
        existingPoint.setPointTmplId(10L);
        existingPoint.setDeviceDeployId(20L);
        existingPoint.setPointName("Temperature");
        existingPoint.setN4Address("A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature");
        existingPoint.setDataType(null);
        existingPoint.setUnit("C");
        existingPoint.setDisplayPrecision(0);
        existingPoint.setAllPath("all/path");
        existingPoint.setType("numeric");
        existingPoint.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingPoint));
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(existingPoint.getN4Address())))
                .thenReturn(List.of(existingPoint));
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,dataType,unit",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature,,C");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        verify(pointDeploymentMapper, never()).updateImportedMetadata(any(PointDeployment.class));
    }

    @Test
    void looksUpOnlyPointDeploymentsPresentInImport() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);
        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        String n4Address = "A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature";
        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(n4Address))).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path," + n4Address);
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), true);

        assertThat(summary.valid()).isTrue();
        verify(pointDeploymentMapper).findAllByN4Addresses(List.of(n4Address));
    }

    @Test
    void batchesPointDeploymentLookupForLargeImports() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);
        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllByN4Addresses(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String devicePath = "MVP_LARGE_DEMO_20260901_00_Collector_00";
        StringBuilder csv = new StringBuilder("*path,*name,*type,allPath,mqttPath\n");
        for (int index = 0; index < 5001; index++) {
            csv.append(devicePath)
                    .append(",Temperature,numeric,all/path,A1_MVP_A1/MVP_A1/")
                    .append(devicePath)
                    .append("/Temperature_")
                    .append(index)
                    .append('\n');
        }
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), true);

        assertThat(summary.valid()).isTrue();
        verify(pointDeploymentMapper, org.mockito.Mockito.times(2))
                .findAllByN4Addresses(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void doesNotBlockImportResponseWhileSchedulingRedisSync() throws Exception {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of());
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        CountDownLatch syncStarted = new CountDownLatch(1);
        CountDownLatch releaseSync = new CountDownLatch(1);
        doAnswer(invocation -> {
            syncStarted.countDown();
            releaseSync.await(2, TimeUnit.SECONDS);
            return null;
        }).when(alarmRuleSyncService).syncAllRulesToRedis();

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "deployment.csv",
                "text/csv",
                "*path,*name,*type,allPath,mqttPath\n".getBytes(StandardCharsets.UTF_8));
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<ImportSummary> importFuture = callerExecutor.submit(
                    () -> service.importFiles(List.of(file), false));

            assertThat(syncStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(importFuture).isDone();

            releaseSync.countDown();
            ImportSummary summary = importFuture.get(2, TimeUnit.SECONDS);
            assertThat(summary.valid()).isTrue();
            assertThat(summary.committed()).isTrue();
        } finally {
            releaseSync.countDown();
            callerExecutor.shutdownNow();
        }
    }

    @Test
    void skipsAlarmCodeWriteWhenImportedAlarmCodeIsUnchanged() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setUnit("C");
        pointTemplate.setAlarmCode("Temperature_00");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setSystemName("A1_MVP_A1/MVP_A1");
        existingDevice.setDeviceDeployName("MVP_A1_00_Collector_00");
        existingDevice.setIsDeleted(false);

        PointDeployment existingPoint = new PointDeployment();
        existingPoint.setPointDeployId(30L);
        existingPoint.setPointTmplId(10L);
        existingPoint.setDeviceDeployId(20L);
        existingPoint.setAlarmCode("Temperature_00");
        existingPoint.setPointName("Temperature");
        existingPoint.setN4Address("A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature");
        existingPoint.setDataType(null);
        existingPoint.setUnit("C");
        existingPoint.setDisplayPrecision(0);
        existingPoint.setAllPath("all/path");
        existingPoint.setType("numeric");
        existingPoint.setIsDeleted(false);

        AlarmRuleNumericEntity existingRule = new AlarmRuleNumericEntity();
        existingRule.setAlarmName("Temperature_00");
        existingRule.setPointType("numeric");
        existingRule.setTimeDelay(0);
        existingRule.setHighValue("10");
        existingRule.setHighSeverity("Major");
        existingRule.setHighMessage("High");
        existingRule.setLowValue("0");
        existingRule.setLowSeverity("Major");
        existingRule.setLowMessage("Low");

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingPoint));
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(existingPoint.getN4Address())))
                .thenReturn(List.of(existingPoint));
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of(existingRule));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,alarmTimeDelay,alarmClass,"
                        + "outOfRangeAlgorithmHighLimit,outOfRangeAlgorithmHighLimitText,"
                        + "outOfRangeAlgorithmLowLimit,outOfRangeAlgorithmLowLimitText",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature,0,Major,10,High,0,Low");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        verify(pointDeploymentMapper, never()).updateAlarmCodesBatch(
                org.mockito.ArgumentMatchers.anyList());
        verify(pointTemplateMapper, never()).updateAlarmCode(10L, "Temperature_00");
    }

    @Test
    void updatesChangedAlarmCodesInOneBatch() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setUnit("C");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setSystemName("A1_MVP_A1/MVP_A1");
        existingDevice.setDeviceDeployName("MVP_A1_00_Collector_00");
        existingDevice.setIsDeleted(false);

        PointDeployment existingPoint = new PointDeployment();
        existingPoint.setPointDeployId(30L);
        existingPoint.setPointTmplId(10L);
        existingPoint.setDeviceDeployId(20L);
        existingPoint.setAlarmCode("OldAlarm");
        existingPoint.setPointName("Temperature");
        existingPoint.setN4Address("A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature");
        existingPoint.setDataType(null);
        existingPoint.setUnit("C");
        existingPoint.setDisplayPrecision(0);
        existingPoint.setAllPath("all/path");
        existingPoint.setType("numeric");
        existingPoint.setIsDeleted(false);

        AlarmRuleNumericEntity existingRule = new AlarmRuleNumericEntity();
        existingRule.setAlarmName("Temperature_00");
        existingRule.setPointType("numeric");
        existingRule.setTimeDelay(0);
        existingRule.setHighValue("10");
        existingRule.setHighSeverity("Major");
        existingRule.setHighMessage("High");
        existingRule.setLowValue("0");
        existingRule.setLowSeverity("Major");
        existingRule.setLowMessage("Low");

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingPoint));
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(existingPoint.getN4Address())))
                .thenReturn(List.of(existingPoint));
        when(navNodeMapper.findAll()).thenReturn(List.of());
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of(existingRule));

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath,alarmTimeDelay,alarmClass,"
                        + "outOfRangeAlgorithmHighLimit,outOfRangeAlgorithmHighLimitText,"
                        + "outOfRangeAlgorithmLowLimit,outOfRangeAlgorithmLowLimitText",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + "A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature,0,Major,10,High,0,Low");
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var summary = service.importFiles(List.of(file), false);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.committed()).isTrue();
        var batchCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pointDeploymentMapper).updateAlarmCodesBatch(batchCaptor.capture());
        @SuppressWarnings("unchecked")
        List<PointDeployment> updates = (List<PointDeployment>) batchCaptor.getValue();
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).getN4Address()).isEqualTo(existingPoint.getN4Address());
        assertThat(updates.get(0).getAlarmCode()).isEqualTo("Temperature_00");
        verify(pointTemplateMapper, never()).updateAlarmCode(10L, "Temperature_00");
        verify(pointTemplateMapper).updateAlarmCodesBatch(
                org.mockito.ArgumentMatchers.anyList());
    }
}
