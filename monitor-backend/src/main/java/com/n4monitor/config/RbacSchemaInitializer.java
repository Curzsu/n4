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
                log.info("RBAC tables already exist in database. Skipping schema initialization.");
            }
        } catch (Exception e) {
            log.error("Failed to check/initialize RBAC schema: {}", e.getMessage(), e);
        }
    }
}
