package com.fantasta.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/ws/round")
@ApplicationScoped
public class RoundSocket {

    private static final Logger LOG = Logger.getLogger(RoundSocket.class);
    private final Set<WebSocketConnection> conns = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper M = new ObjectMapper();

    @OnOpen
    public void onOpen(WebSocketConnection c) {
        conns.add(c);
    }

    @OnClose
    public void onClose(WebSocketConnection c) {
        conns.remove(c);
    }

    @OnError
    public void onError(WebSocketConnection c, Throwable t) {
        conns.remove(c);
        LOG.warnf(t, "WebSocket connection failed: %s", c.id());
    }

    public void broadcast(String type, Object payload) {
        String json;
        try {
            json = M.writeValueAsString(
                    java.util.Map.of("type", type, "payload", payload)
            );
        } catch (Exception e) {
            LOG.warnf(e, "Unable to serialize WebSocket message type=%s", type);
            return;
        }

        for (var c : conns) {
            if (!c.isOpen()) {
                conns.remove(c);
                continue;
            }

            c.sendText(json)
                    .subscribe()
                    .with(
                            ignored -> {},
                            failure -> {
                                conns.remove(c);
                                LOG.warnf(failure, "Unable to send WebSocket message to %s", c.id());
                            }
                    );
        }
    }

    int connectionCount() {
        return conns.size();
    }
}
