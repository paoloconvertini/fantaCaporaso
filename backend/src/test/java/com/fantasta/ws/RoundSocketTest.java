package com.fantasta.ws;

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoundSocketTest {

    @Test
    void broadcastSendsMessageToSixteenOpenConnections() {
        RoundSocket socket = new RoundSocket();
        List<List<String>> receivedMessages = new ArrayList<>();

        for (int i = 0; i < 16; i++) {
            List<String> received = new ArrayList<>();
            receivedMessages.add(received);
            socket.onOpen(connection("conn-" + i, true, received, false));
        }

        socket.broadcast("BID_ADDED", java.util.Map.of("user", "angelo", "amount", 10));

        assertEquals(16, socket.connectionCount());
        assertEquals(16, receivedMessages.stream().filter(messages -> messages.size() == 1).count());
        assertTrue(receivedMessages.get(0).get(0).contains("\"type\":\"BID_ADDED\""));
    }

    @Test
    void broadcastRemovesClosedConnections() {
        RoundSocket socket = new RoundSocket();
        List<String> received = new ArrayList<>();

        socket.onOpen(connection("open", true, received, false));
        socket.onOpen(connection("closed", false, new ArrayList<>(), false));

        socket.broadcast("ROUND_STARTED", java.util.Map.of("roundId", "r1"));

        assertEquals(1, socket.connectionCount());
        assertEquals(1, received.size());
    }

    @Test
    void broadcastRemovesFailingConnectionsWithoutBlockingOthers() {
        RoundSocket socket = new RoundSocket();
        List<String> received = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        socket.onOpen(connection("failing", true, new ArrayList<>(), true, failures));
        socket.onOpen(connection("healthy", true, received, false));

        socket.broadcast("ROUND_CLOSED", java.util.Map.of("roundId", "r1"));

        assertEquals(1, failures.get());
        assertEquals(1, socket.connectionCount());
        assertEquals(1, received.size());
    }

    private WebSocketConnection connection(String id, boolean open, List<String> received, boolean failOnSend) {
        return connection(id, open, received, failOnSend, new AtomicInteger());
    }

    private WebSocketConnection connection(
            String id,
            boolean open,
            List<String> received,
            boolean failOnSend,
            AtomicInteger failures
    ) {
        return (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class[]{WebSocketConnection.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "id" -> id;
                        case "isOpen" -> open;
                        case "isClosed" -> !open;
                        case "sendText" -> {
                            if (failOnSend) {
                                failures.incrementAndGet();
                                yield Uni.createFrom().failure(new RuntimeException("send failed"));
                            }
                            received.add(String.valueOf(args[0]));
                            yield Uni.createFrom().voidItem();
                        }
                        case "toString" -> id;
                        default -> defaultValue(method.getReturnType());
                    };
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == long.class || returnType == double.class || returnType == float.class) {
            return 0;
        }
        return null;
    }
}
