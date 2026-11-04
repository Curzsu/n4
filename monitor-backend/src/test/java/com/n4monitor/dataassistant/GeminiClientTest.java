package com.n4monitor.dataassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiClientTest {
    @Test
    void sendsGeminiRequestsThroughConfiguredProxy() throws Exception {
        try (var proxy = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            proxy.setSoTimeout(5000);
            var requestLine = CompletableFuture.supplyAsync(() -> receiveConnect(proxy));

            var client = new GeminiClient(new ObjectMapper(), "test-key",
                    "http://127.0.0.1:" + proxy.getLocalPort());
            var question = new DataAssistantService.Question("Count deployed devices", List.of());
            assertThrows(BusinessException.class,
                    () -> client.plan(question, List.of("deployments"), LocalDate.of(2026, 11, 4)));

            assertEquals("CONNECT generativelanguage.googleapis.com:443 HTTP/1.1",
                    requestLine.get(5, TimeUnit.SECONDS));
        }
    }

    private static String receiveConnect(ServerSocket proxy) {
        try (var connection = proxy.accept()) {
            connection.setSoTimeout(5000);
            var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            while (true) {
                String header = reader.readLine();
                if (header == null || header.isEmpty()) {
                    break;
                }
            }
            connection.getOutputStream().write(
                    "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
            return requestLine;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
