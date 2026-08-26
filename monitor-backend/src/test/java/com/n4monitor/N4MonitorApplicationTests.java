package com.n4monitor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Starts the full context including live middleware (MySQL/Redis/Kafka/TDengine/Flink); "
        + "run manually for a local smoke test, kept out of automated builds")
@SpringBootTest
class N4MonitorApplicationTests {

    @Test
    void contextLoads() {
    }

}
