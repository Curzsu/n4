package com.n4monitor.config;

import com.n4monitor.alarm.listener.AlarmNotificationListener;
import com.n4monitor.alarm.listener.AlarmRealtimeListener;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.ArrayOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis Pub/Sub configuration for alarm notifications and realtime persistence.
 * <p>
 * Bridges Flink alarm processing with:
 * <ol>
 *   <li>WebSocket broadcasting ({@code alarm:notifications})
 *   <li>Real-time TDengine persistence ({@code alarm:realtime})
 * </ol>
 * <p>
 * Channel names are configurable via {@code alarm.redis.notification-channel}
 * and {@code alarm.redis.realtime-channel}.
 * <p>
 * Resilience: the subscription connection (Lettuce) is kept alive by the
 * TCP-keepalive settings in {@link RedisLettuceCustomizer}. As a fallback
 * that does not depend on the native transport, {@link #checkSubscriptionHealth()}
 * periodically verifies via {@code PUBSUB NUMSUB} that both channels still have
 * a live subscriber and force-restarts the container if not. This covers the
 * silent half-open failure mode where neither keepalive nor Lettuce's watchdog
 * surfaces an exception.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Value("${alarm.redis.notification-channel}")
    private String alarmNotificationChannel;

    @Value("${alarm.redis.realtime-channel}")
    private String alarmRealtimeChannel;

    private final AlarmNotificationListener alarmNotificationListener;
    private final AlarmRealtimeListener alarmRealtimeListener;
    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    private RedisMessageListenerContainer container;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(alarmNotificationListener,
                new ChannelTopic(alarmNotificationChannel));
        container.addMessageListener(alarmRealtimeListener,
                new ChannelTopic(alarmRealtimeChannel));
        return container;
    }

    @PostConstruct
    public void logConfig() {
        log.info("Redis Pub/Sub configured: notification-channel='{}', realtime-channel='{}'",
                alarmNotificationChannel, alarmRealtimeChannel);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean running = container != null && container.isRunning();
        log.info("Pub/Sub listener container running={}, subscribed channels: [{}], [{}]",
                running, alarmNotificationChannel, alarmRealtimeChannel);
    }

    /**
     * Periodic subscription health check. Queries {@code PUBSUB NUMSUB} for both
     * channels; if either has zero subscribers the subscription connection is
     * presumed dead (half-open) and the container is force-restarted so Spring
     * re-establishes the subscription. This is a safety net on top of the
     * Lettuce TCP-keepalive settings.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void checkSubscriptionHealth() {
        if (container == null) {
            return;
        }
        try {
            // PUBSUB NUMSUB returns an alternating list: [channel1, count1, channel2, count2].
            // Use the native Lettuce connection with an explicit ArrayOutput instead of
            // RedisConnection.execute("PUBSUB", ...): the generic dispatch path maps the
            // output type incorrectly for subcommand-style commands and throws
            // "Unknown redis exception" on Lettuce.
            //
            // getNativeConnection() returns a RedisClusterAsyncCommands (the async command
            // API), which is NOT itself a StatefulRedisConnection - it holds one and
            // exposes it via getStatefulConnection(). That stateful connection is what
            // provides dispatch() with a custom CommandOutput.
            Object raw = redisTemplate.execute((RedisCallback<Object>) connection -> {
                Object nativeConn = connection.getNativeConnection();
                // RedisClusterAsyncCommands extends RedisAsyncCommands, which declares
                // getStatefulConnection() returning the underlying StatefulRedisConnection.
                RedisAsyncCommands<byte[], byte[]> asyncCommands =
                        (RedisAsyncCommands<byte[], byte[]>) nativeConn;
                StatefulRedisConnection<byte[], byte[]> stateful = asyncCommands.getStatefulConnection();
                return stateful.sync().dispatch(
                        CommandType.PUBSUB,
                        new ArrayOutput<>(ByteArrayCodec.INSTANCE),
                        new CommandArgs<>(ByteArrayCodec.INSTANCE)
                                .add("NUMSUB")
                                .add(alarmNotificationChannel.getBytes(StandardCharsets.UTF_8))
                                .add(alarmRealtimeChannel.getBytes(StandardCharsets.UTF_8)));
            });
            @SuppressWarnings("unchecked")
            List<Object> numsubResult = (List<Object>) raw;

            long notifCount = extractNumsub(numsubResult, alarmNotificationChannel);
            long realtimeCount = extractNumsub(numsubResult, alarmRealtimeChannel);

            if (notifCount == 0 || realtimeCount == 0) {
                log.warn("Subscription health check failed: numsub {}={}, {}={} - restarting listener container",
                        alarmNotificationChannel, notifCount, alarmRealtimeChannel, realtimeCount);
                restartContainer();
            }
        } catch (Exception e) {
            log.warn("Subscription health check error, restarting listener container: {}",
                    e.getMessage());
            restartContainer();
        }
    }

    @SuppressWarnings("unchecked")
    private long extractNumsub(List<Object> raw, String channel) {
        if (raw == null) {
            return -1;
        }
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            String name = new String((byte[]) raw.get(i));
            if (name.equals(channel)) {
                Object count = raw.get(i + 1);
                if (count instanceof byte[] b) {
                    return Long.parseLong(new String(b));
                }
                return Long.parseLong(String.valueOf(count));
            }
        }
        return -1;
    }

    private void restartContainer() {
        try {
            container.stop();
        } catch (Exception e) {
            log.warn("Error stopping listener container: {}", e.getMessage());
        }
        try {
            container.start();
            log.info("Listener container restarted, re-subscribed to channels: [{}], [{}]",
                    alarmNotificationChannel, alarmRealtimeChannel);
        } catch (Exception e) {
            log.error("Failed to restart listener container: {}", e.getMessage(), e);
        }
    }
}
