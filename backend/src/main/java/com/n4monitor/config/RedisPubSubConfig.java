package com.n4monitor.config;

import com.n4monitor.websocket.AlarmNotificationListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub configuration for alarm notifications.
 * Bridges Flink alarm processing with WebSocket broadcasting.
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    public static final String ALARM_CHANNEL = "alarm:notifications";

    private final AlarmNotificationListener alarmNotificationListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Use the listener directly (it implements MessageListener interface)
        container.addMessageListener(alarmNotificationListener, new ChannelTopic(ALARM_CHANNEL));
        return container;
    }
}
