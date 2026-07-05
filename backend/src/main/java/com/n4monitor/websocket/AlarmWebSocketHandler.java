package com.n4monitor.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time alarm data broadcasting.
 * Manages connected client sessions and provides broadcast capability.
 * Uses synchronized blocks to prevent concurrent write errors.
 */
@Slf4j
@Component
public class AlarmWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Broadcast a message to all connected WebSocket clients.
     * Uses synchronized per-session to prevent concurrent write errors.
     *
     * @param message JSON string to broadcast
     */
    public void broadcast(String message) {
        if (sessions.isEmpty()) {
            log.debug("No WebSocket clients connected, skipping broadcast");
            return;
        }
        log.info("Broadcasting alarm update to {} WebSocket clients", sessions.size());
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                // Synchronize per session to prevent concurrent write errors
                synchronized (session) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message to session {}: {}",
                                session.getId(), e.getMessage());
                    } catch (IllegalStateException e) {
                        log.warn("WebSocket session {} in invalid state, removing: {}",
                                session.getId(), e.getMessage());
                        sessions.remove(session);
                    }
                }
            }
        }
    }

    /**
     * Get the number of currently connected clients.
     */
    public int getConnectedCount() {
        return sessions.size();
    }
}
