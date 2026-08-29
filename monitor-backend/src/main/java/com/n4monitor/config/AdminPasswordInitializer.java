package com.n4monitor.config;

import com.n4monitor.entity.SysUser;
import com.n4monitor.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminPasswordInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminPasswordInitializer.class);
    private static final String DEFAULT_PASSWORD_PLACEHOLDER = "{DEFAULT_ADMIN_PASSWORD}";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public AdminPasswordInitializer(SysUserMapper userMapper,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${admin.default-password:admin123}") String defaultPassword) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            SysUser admin = userMapper.findByUsername("admin");
            if (admin != null && (DEFAULT_PASSWORD_PLACEHOLDER.equals(admin.getPassword()) || admin.getPassword() == null || admin.getPassword().isEmpty())) {
                userMapper.updatePassword(admin.getUserId(), passwordEncoder.encode(defaultPassword));
                log.info("Initialized default admin password successfully");
            }
        } catch (Exception e) {
            log.warn("Admin password initialization check skipped or deferred: {}", e.getMessage());
        }
    }
}
