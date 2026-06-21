package com.n4monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.n4monitor.mapper")
public class N4MonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(N4MonitorApplication.class, args);
    }
}
