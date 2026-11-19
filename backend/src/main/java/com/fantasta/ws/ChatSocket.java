package com.fantasta.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

@WebSocket(path = "/ws/chat")
@RolesAllowed({"admin", "user", "observer"})
@ApplicationScoped
public class ChatSocket {
    private static final int MAX_MESSAGES = 50;
    private static final int MAX_LENGTH = 300;
    private static final int MAX_PENDING_EVENTS = 32;
    private static final long SEND_INTERVAL_MS = 1000;
    private static final long PRESENCE_TIMEOUT_MS = 45000;

    private final Map<WebSocketConnection, Client> clients = new HashMap<>();
    private final Deque<Message> messages = new ArrayDeque<>();
    private final Map<String, Long> lastMessages = new HashMap<>();

    @Inject ObjectMapper json;
    @Inject SecurityIdentity identity;

    @OnOpen
    public synchronized void open(WebSocketConnection connection) {
        String origin = connection.handshakeRequest().header("Origin");
        String host = connection.handshakeRequest().header("Host");
        if (origin == null || host == null ||
                !(origin.equals("https://" + host) || origin.equals("http://" + host))) {
            connection.close().subscribe().with(ignored -> {}, ignored -> {});
            return;
        }
        Client client = new Client(identity.getPrincipal().getName(), identity.getAttribute("participant_id"),
                identity.hasRole("admin"));
        clients.put(connection, client);
        send(connection, client, encode(Map.of("type", "history", "messages", List.copyOf(messages))));
        if (client.participantId != null) presenceChanged();
    }

    @OnTextMessage
    public synchronized void receive(WebSocketConnection connection, String frame) {
        Client client = clients.get(connection);
        if (client == null || frame.length() > MAX_LENGTH + 1) return;
        long now = System.currentTimeMillis();
        if (frame.equals("ping")) {
            boolean expired = now - client.lastSeen > PRESENCE_TIMEOUT_MS;
            client.lastSeen = now;
            if (client.admin) {
                Set<Long> online = onlineTeams();
                send(connection, client, encode(Map.of("type", "pong", "presenceChanged", !online.equals(client.onlineTeams))));
                client.onlineTeams = online;
            } else {
                send(connection, client, encode(Map.of("type", "pong")));
            }
            if (expired && client.participantId != null) presenceChanged();
            return;
        }
        if (!frame.startsWith(":")) return;
        String text = frame.substring(1).strip();
        if (text.isEmpty() || now - lastMessages.getOrDefault(client.username, 0L) < SEND_INTERVAL_MS) return;
        lastMessages.put(client.username, now);
        Message message = new Message(client.username, text);
        if (messages.size() == MAX_MESSAGES) messages.removeFirst();
        messages.addLast(message);
        broadcast(Map.of("type", "message", "message", message), false);
    }

    @OnClose
    public synchronized void close(WebSocketConnection connection) {
        Client client = clients.remove(connection);
        if (client == null) return;
        if (clients.values().stream().noneMatch(other -> other.username.equals(client.username))) {
            lastMessages.remove(client.username);
        }
        if (client.participantId != null) presenceChanged();
    }

    public synchronized Set<Long> onlineTeams() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TIMEOUT_MS;
        Set<Long> teams = new HashSet<>();
        clients.forEach((connection, client) -> {
            if (connection.isOpen() && client.participantId != null && client.lastSeen >= cutoff) {
                teams.add(client.participantId);
            }
        });
        return teams;
    }

    private void presenceChanged() {
        broadcast(Map.of("type", "presence"), true);
    }

    private void broadcast(Object event, boolean adminsOnly) {
        String payload = encode(event);
        Map.copyOf(clients).forEach((connection, client) -> {
            if (!adminsOnly || client.admin) send(connection, client, payload);
        });
    }

    private String encode(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile serializzare il messaggio chat", e);
        }
    }

    private void send(WebSocketConnection connection, Client client, String payload) {
        if (!connection.isOpen() || client.closing) return;
        if (client.pending.size() >= MAX_PENDING_EVENTS) {
            disconnect(connection, client);
            return;
        }
        client.pending.addLast(payload);
        if (client.pending.size() == 1) sendNext(connection, client);
    }

    private void sendNext(WebSocketConnection connection, Client client) {
        connection.sendText(client.pending.getFirst()).subscribe().with(
                ignored -> sent(connection, client),
                failure -> disconnect(connection, client));
    }

    private synchronized void sent(WebSocketConnection connection, Client client) {
        if (client.closing) return;
        client.pending.removeFirst();
        if (!client.pending.isEmpty()) sendNext(connection, client);
    }

    private synchronized void disconnect(WebSocketConnection connection, Client client) {
        client.closing = true;
        client.pending.clear();
        connection.close().subscribe().with(ignored -> {}, ignored -> {});
    }

    private static class Client {
        final String username;
        final Long participantId;
        final boolean admin;
        long lastSeen = System.currentTimeMillis();
        final Deque<String> pending = new ArrayDeque<>();
        Set<Long> onlineTeams = Set.of();
        boolean closing;

        Client(String username, Long participantId, boolean admin) {
            this.username = username;
            this.participantId = participantId;
            this.admin = admin;
        }
    }

    public record Message(String author, String text) {}
}
