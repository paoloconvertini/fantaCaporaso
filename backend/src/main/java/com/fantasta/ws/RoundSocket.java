package com.fantasta.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/ws/round")
@ApplicationScoped
public class RoundSocket {
    private final Set<WebSocketConnection> conns = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper M = new ObjectMapper();

    @OnOpen
    public void onOpen(WebSocketConnection c) {
        conns.add(c);
    }

    public void broadcast(String type, Object payload) {
        try {
            String json = M.writeValueAsString(java.util.Map.of("type", type, "payload", payload));
            for (var c : conns) if (c.isOpen()) c.sendTextAndAwait(json);
        } catch (Exception ignored) {
        }
    }
}
