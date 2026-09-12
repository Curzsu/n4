package com.n4monitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class RbacSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RbacSchemaInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public RbacSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'sys_user'",
                    Integer.class);
            if (count == null || count == 0) {
                log.info("RBAC tables not found in database. Initializing RBAC schema from schema_rbac.sql...");
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                File sqlFile = new File("deploy/schema_rbac.sql");
                if (!sqlFile.exists()) {
                    sqlFile = new File("../deploy/schema_rbac.sql");
                }
                if (sqlFile.exists()) {
                    populator.addScript(new FileSystemResource(sqlFile));
                } else {
                    populator.addScript(new ClassPathResource("schema_rbac.sql"));
                }
                populator.setContinueOnError(false);
                populator.execute(dataSource);
                log.info("RBAC schema and seed data initialized successfully.");
            } else {
                log.info("RBAC tables already exist in database. Synchronizing the permission catalog.");
            }
            syncPermissionCatalog();
        } catch (Exception e) {
            log.error("Failed to check/initialize RBAC schema: {}", e.getMessage(), e);
        }
    }

    private void syncPermissionCatalog() {
        migrateLegacySystemRoot();

        Map<String, Long> permissionIds = new HashMap<>();
        for (RbacPermissionCatalog.PermissionDefinition definition : RbacPermissionCatalog.definitions()) {
            Long parentId = definition.parentCode() == null
                    ? 0L
                    : permissionIds.get(definition.parentCode());
            if (parentId == null) {
                parentId = findPermissionId(definition.parentCode());
            }
            if (parentId == null) {
                throw new IllegalStateException("Missing parent permission: " + definition.parentCode());
            }

            jdbcTemplate.update("""
                    INSERT INTO sys_permission
                        (perm_name, perm_code, perm_type, parent_id, path, component, icon, sort_order, status, is_deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
                    ON DUPLICATE KEY UPDATE
                        perm_name = VALUES(perm_name),
                        perm_type = VALUES(perm_type),
                        parent_id = VALUES(parent_id),
                        path = VALUES(path),
                        component = VALUES(component),
                        icon = VALUES(icon),
                        sort_order = VALUES(sort_order),
                        status = 1,
                        is_deleted = 0
                    """,
                    definition.name(),
                    definition.code(),
                    definition.type(),
                    parentId,
                    definition.path(),
                    definition.component(),
                    definition.icon(),
                    definition.sortOrder());

            Long permissionId = findPermissionId(definition.code());
            if (permissionId == null) {
                throw new IllegalStateException("Permission upsert did not create a row: " + definition.code());
            }
            permissionIds.put(definition.code(), permissionId);
        }

        jdbcTemplate.update("""
                INSERT IGNORE INTO sys_group_permission (group_id, perm_id)
                SELECT group_record.group_id, permission_record.perm_id
                FROM sys_group group_record
                CROSS JOIN sys_permission permission_record
                WHERE group_record.group_code = 'SUPER_ADMIN'
                  AND group_record.is_deleted = 0
                  AND permission_record.is_deleted = 0
                """);

        for (int pass = 0; pass < RbacPermissionCatalog.definitions().size(); pass++) {
            int inserted = jdbcTemplate.update("""
                    INSERT IGNORE INTO sys_group_permission (group_id, perm_id)
                    SELECT DISTINCT group_permission.group_id, parent.perm_id
                    FROM sys_group_permission group_permission
                    JOIN sys_permission child ON child.perm_id = group_permission.perm_id
                    JOIN sys_permission parent ON parent.perm_id = child.parent_id
                    WHERE child.parent_id > 0
                      AND child.is_deleted = 0
                      AND parent.is_deleted = 0
                    """);
            if (inserted == 0) {
                break;
            }
        }

        log.info("Canonical RBAC permission catalog synchronized: {} definitions.",
                RbacPermissionCatalog.definitions().size());
    }

    private Long findPermissionId(String code) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT perm_id FROM sys_permission WHERE perm_code = ? AND is_deleted = 0",
                (resultSet, rowNum) -> resultSet.getLong("perm_id"),
                code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void migrateLegacySystemRoot() {
        Integer canonicalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE perm_code = 'system'",
                Integer.class);
        if (canonicalCount != null && canonicalCount == 0) {
            jdbcTemplate.update("""
                    UPDATE sys_permission
                    SET perm_code = 'system', perm_name = 'system'
                    WHERE perm_code = 'system:manage'
                    """);
        } else {
            jdbcTemplate.update("""
                    UPDATE sys_permission legacy
                    JOIN sys_permission canonical ON canonical.perm_code = 'system'
                    SET legacy.is_deleted = 1
                    WHERE legacy.perm_code = 'system:manage'
                      AND legacy.perm_id <> canonical.perm_id
                    """);
        }
    }
}
