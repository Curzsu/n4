package com.n4monitor.alarm.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlarmRuleSchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public AlarmRuleSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS alarm_rule (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                alarm_name VARCHAR(255) NOT NULL,
                point_type VARCHAR(32) NOT NULL,
                boolean_value VARCHAR(255) NULL,
                boolean_severity VARCHAR(64) NULL,
                boolean_message VARCHAR(1000) NULL,
                high_value VARCHAR(255) NULL,
                high_severity VARCHAR(64) NULL,
                high_message VARCHAR(1000) NULL,
                high_hysteresis VARCHAR(255) NULL,
                high_high_value VARCHAR(255) NULL,
                high_high_severity VARCHAR(64) NULL,
                high_high_message VARCHAR(1000) NULL,
                high_high_hysteresis VARCHAR(255) NULL,
                low_value VARCHAR(255) NULL,
                low_severity VARCHAR(64) NULL,
                low_message VARCHAR(1000) NULL,
                low_hysteresis VARCHAR(255) NULL,
                low_low_value VARCHAR(255) NULL,
                low_low_severity VARCHAR(64) NULL,
                low_low_message VARCHAR(1000) NULL,
                low_low_hysteresis VARCHAR(255) NULL,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                is_deleted TINYINT NOT NULL DEFAULT 0,
                UNIQUE KEY uk_alarm_rule_name (alarm_name),
                INDEX idx_alarm_rule_deleted (is_deleted)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }
}
