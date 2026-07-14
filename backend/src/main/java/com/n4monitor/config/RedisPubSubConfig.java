package com.n4monitor.config;

import com.n4monitor.websocket.AlarmNotificationListener;
import com.n4monitor.websocket.AlarmRealtimeListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

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
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Value("${alarm.redis.notification-channel:alarm:notifications}")
    private String alarmNotificationChannel;

    @Value("${alarm.redis.realtime-channel:alarm:realtime}")
    private String alarmRealtimeChannel;

    private final AlarmNotificationListener alarmNotificationListener;
    private final AlarmRealtimeListener alarmRealtimeListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(alarmNotificationListener,
                new ChannelTopic(alarmNotificationChannel));
        container.addMessageListener(alarmRealtimeListener,
                new ChannelTopic(alarmRealtimeChannel));
        return container;
    }
}
