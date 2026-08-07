package com.n4monitor.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Lettuce client configuration for the Redis connections used by Spring Data Redis.
 * <p>
 * Purpose: harden the Redis subscription connection against silent half-open
 * failures over a remote/NAT network. The default Lettuce {@link SocketOptions}
 * disables TCP keepalive, so a pub/sub subscription socket that is idle (no
 * published messages) for a while can be silently reclaimed by an intermediate
 * NAT/firewall. The Lettuce {@code ConnectionWatchdog} is reactive: it only
 * reconnects on a detected socket failure. Without keepalive probes no failure
 * is ever detected, the {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}
 * receives no exception, and the subscription stays dead forever (zero error
 * logs) until the backend is restarted.
 * <p>
 * Fix (per the Redis/Lettuce production-usage guide):
 * <ul>
 *   <li>{@code TCP_KEEPALIVE} (idle/interval/count) - detect dead connections
 *       with no traffic (NAT idle drop). The {@code SO_KEEPALIVE} toggle
 *       applies on every transport; the fine-grained idle/interval/count
 *       values require a Netty native transport (epoll on Linux, kqueue on
 *       macOS). On plain NIO (e.g. Windows dev) only the toggle takes effect
 *       and OS defaults govern probe timing.</li>
 *   <li>{@code TCP_USER_TIMEOUT} - detect connections where the server stops
 *       ACKing. This option is only applicable to native transports; applying
 *       it to an NIO channel logs a warning, so it is added conditionally.</li>
 * </ul>
 * When the OS reports the socket as dead, Lettuce reconnects and its
 * {@code activated()} callback resubscribes to all channels automatically.
 */
@Configuration
public class RedisLettuceCustomizer {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
        return builder -> {
            SocketOptions.Builder socketOptionsBuilder = SocketOptions.builder();

            // TCP_KEEPALIVE: probe idle connections so half-open sockets are detected.
            // SO_KEEPALIVE applies on all transports; the fine-grained idle/interval/count
            // take effect only with a native transport (epoll/kqueue).
            SocketOptions.KeepAliveOptions keepAliveOptions = SocketOptions.KeepAliveOptions.builder()
                    .idle(Duration.ofSeconds(15))    // TCP_KEEPIDLE - start probing after 15s idle
                    .interval(Duration.ofSeconds(5)) // TCP_KEEPINTVL - probe every 5s
                    .count(3)                          // TCP_KEEPCNT - 3 failed probes => dead
                    .enable()
                    .build();
            socketOptionsBuilder.keepAlive(keepAliveOptions);

            // TCP_USER_TIMEOUT: fail connections whose last request is never ACKed.
            // Only applicable to native transports (epoll/kqueue); on NIO it logs a
            // warning, so enable it only when a native transport is available.
            if (Epoll.isAvailable() || KQueue.isAvailable()) {
                SocketOptions.TcpUserTimeoutOptions tcpUserTimeout =
                        SocketOptions.TcpUserTimeoutOptions.builder()
                                .tcpUserTimeout(Duration.ofSeconds(20))
                                .enable()
                                .build();
                socketOptionsBuilder.tcpUserTimeout(tcpUserTimeout);
            }

            SocketOptions socketOptions = socketOptionsBuilder.build();

            builder.clientOptions(ClientOptions.builder()
                            .socketOptions(socketOptions)
                            .build())
                    .commandTimeout(Duration.ofSeconds(30));
        };
    }
}
