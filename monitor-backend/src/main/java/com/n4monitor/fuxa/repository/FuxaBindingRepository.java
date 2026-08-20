package com.n4monitor.fuxa.repository;

import com.n4monitor.fuxa.entity.FuxaDeviceBinding;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class FuxaBindingRepository implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;

    public FuxaBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        // Table creation moved to explicit migration scripts if needed
    }

    public Optional<FuxaDeviceBinding> findByDeviceId(Long deviceId) {
        return jdbcTemplate.query(
            "SELECT * FROM fuxa_device_binding WHERE device_id = ? LIMIT 1",
            this::mapRow,
            deviceId
        ).stream().findFirst();
    }

    public Optional<FuxaDeviceBinding> findByMqttDeviceId(String mqttDeviceId) {
        return jdbcTemplate.query(
            "SELECT * FROM fuxa_device_binding WHERE mqtt_device_id = ? LIMIT 1",
            this::mapRow,
            mqttDeviceId
        ).stream().findFirst();
    }

    public void save(FuxaDeviceBinding binding) {
        jdbcTemplate.update("""
            INSERT INTO fuxa_device_binding
                (mqtt_device_id, device_id, device_code, model_code, fuxa_device_id,
                 fuxa_device_name, view_name, provision_status, last_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                device_id = VALUES(device_id), device_code = VALUES(device_code),
                model_code = VALUES(model_code), fuxa_device_id = VALUES(fuxa_device_id),
                fuxa_device_name = VALUES(fuxa_device_name), view_name = VALUES(view_name),
                provision_status = VALUES(provision_status), last_error = VALUES(last_error)
            """,
            binding.getMqttDeviceId(), binding.getDeviceId(), binding.getDeviceCode(),
            binding.getModelCode(), binding.getFuxaDeviceId(), binding.getFuxaDeviceName(),
            binding.getViewName(), binding.getProvisionStatus(), binding.getLastError()
        );
    }

    private FuxaDeviceBinding mapRow(ResultSet rs, int rowNum) throws SQLException {
        FuxaDeviceBinding binding = new FuxaDeviceBinding();
        binding.setMqttDeviceId(rs.getString("mqtt_device_id"));
        binding.setDeviceId(rs.getObject("device_id", Long.class));
        binding.setDeviceCode(rs.getString("device_code"));
        binding.setModelCode(rs.getString("model_code"));
        binding.setFuxaDeviceId(rs.getString("fuxa_device_id"));
        binding.setFuxaDeviceName(rs.getString("fuxa_device_name"));
        binding.setViewName(rs.getString("view_name"));
        binding.setProvisionStatus(rs.getString("provision_status"));
        binding.setLastError(rs.getString("last_error"));
        return binding;
    }
}
