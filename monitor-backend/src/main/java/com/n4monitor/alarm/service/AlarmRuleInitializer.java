package com.n4monitor.alarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Automatically syncs alarm rules from MySQL to Redis on application startup.
 * Runs before the Flink job (Order 20) so rules are available when Flink opens.
 * <p>
 * Retries a few times with back-off because MySQL/Redis containers may come up
 * slightly later than the app; after the last attempt the failure is logged at
 * ERROR - without rules in Redis the whole alarm pipeline stays silent, so a
 * swallowed startup failure must at least be loud.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class AlarmRuleInitializer implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 3_000;

    private final AlarmRuleSyncService alarmRuleSyncService;

    @Override
    public void run(ApplicationArguments args) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Syncing alarm rules from MySQL to Redis (attempt {}/{})...",
                        attempt, MAX_ATTEMPTS);
                alarmRuleSyncService.syncAllRulesToRedis();
                alarmRuleSyncService.syncPointRuleMapToRedis();
                return;
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Auto sync of alarm rules FAILED after {} attempts: the Flink job will "
                            + "start WITHOUT rules and alarm evaluation will be silently dead until "
                            + "the next successful sync (restart the app or fix MySQL/Redis)", MAX_ATTEMPTS, e);
                } else {
                    log.warn("Auto sync attempt {}/{} failed ({}), retrying in {} ms",
                            attempt, MAX_ATTEMPTS, e.getMessage(), RETRY_BACKOFF_MS);
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Alarm rule sync interrupted, giving up", ie);
                        return;
                    }
                }
            }
        }
    }
}
