package com.typeduel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Plain WebSocket race server. Each room is a small state machine:
 * WAITING -> COUNTDOWN -> RACING -> DONE -> (again) WAITING.
 * All room mutations happen inside synchronized(room), so races stay consistent.
 */
@Component
public class RaceHandler extends TextWebSocketHandler {

    private static final List<String> TEXTS = List.of(
        "It works on my machine so I am shipping my machine to production.",
        "A good programmer looks both ways before crossing a one way street.",
        "There are only two hard things in computer science: cache invalidation and naming things.",
        "Debugging is like being the detective in a crime movie where you are also the murderer.",
        "I would tell you a UDP joke but you might not get it.",
        "First solve the problem then write the code and then blame the intern.",
        "The code worked yesterday and I swear I did not touch anything.",
        "Real programmers count from zero and sleep from never.");

    static class Player {
        final String id, name; final WebSocketSession session;
        int pos, rank; boolean finished; double wpm;
        Player(String id, String name, WebSocketSession session) { this.id = id; this.name = name; this.session = session; }
    }

    static class Room {
        final String code; String text = randomText(); String state = "WAITING";
        long startTime; int finishedCount;
        final Map<String, Player> players = new LinkedHashMap<>();
        Room(String code) { this.code = code; }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Room> sessionRoom = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    static String randomText() { return TEXTS.get(ThreadLocalRandom.current().nextInt(TEXTS.size())); }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
        JsonNode msg = mapper.readTree(m.getPayload());
        switch (msg.path("type").asText()) {
            case "join" -> join(s, msg);
            case "start" -> start(s);
            case "progress" -> progress(s, msg.path("pos").asInt());
            case "again" -> again(s);
            default -> { }
        }
    }

    private void join(WebSocketSession s, JsonNode msg) {
        String code = msg.path("room").asText("").trim().toUpperCase();
        String name = msg.path("name").asText("").trim();
        if (!code.matches("[A-Z0-9]{3,8}") || name.isEmpty()) {
            send(s, error("Enter a name and a room code (3-8 letters or numbers)")); return;
        }
        if (name.length() > 16) name = name.substring(0, 16);
        Room room = rooms.computeIfAbsent(code, Room::new);
        synchronized (room) {
            if (!room.state.equals("WAITING")) { send(s, error("That race already started")); return; }
            if (room.players.size() >= 6) { send(s, error("Room is full (6 racers max)")); return; }
            room.players.put(s.getId(), new Player(s.getId(), name, s));
            sessionRoom.put(s.getId(), room);
            ObjectNode you = mapper.createObjectNode();
            you.put("type", "you").put("id", s.getId());
            send(s, you);
            broadcast(room);
        }
    }

    private void start(WebSocketSession s) {
        Room room = sessionRoom.get(s.getId());
        if (room == null) return;
        synchronized (room) {
            if (!room.state.equals("WAITING")) return;
            room.state = "COUNTDOWN";
            broadcast(room);
            scheduler.schedule(() -> {
                synchronized (room) {
                    if (room.state.equals("COUNTDOWN")) {
                        room.state = "RACING";
                        room.startTime = System.currentTimeMillis();
                        broadcast(room);
                    }
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    private void progress(WebSocketSession s, int pos) {
        Room room = sessionRoom.get(s.getId());
        if (room == null) return;
        synchronized (room) {
            Player p = room.players.get(s.getId());
            if (!room.state.equals("RACING") || p == null || p.finished) return;
            int len = room.text.length();
            p.pos = Math.max(0, Math.min(pos, len));
            if (p.pos >= len) {
                p.finished = true;
                p.rank = ++room.finishedCount;
                double minutes = (System.currentTimeMillis() - room.startTime) / 60000.0;
                p.wpm = minutes > 0 ? (len / 5.0) / minutes : 0;
                checkDone(room);
            }
            broadcast(room);
        }
    }

    private void again(WebSocketSession s) {
        Room room = sessionRoom.get(s.getId());
        if (room == null) return;
        synchronized (room) {
            if (!room.state.equals("DONE")) return;
            room.text = randomText();
            room.state = "WAITING";
            room.finishedCount = 0;
            room.players.values().forEach(p -> { p.pos = 0; p.rank = 0; p.wpm = 0; p.finished = false; });
            broadcast(room);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        Room room = sessionRoom.remove(s.getId());
        if (room == null) return;
        synchronized (room) {
            room.players.remove(s.getId());
            if (room.players.isEmpty()) { rooms.remove(room.code); return; }
            if (room.state.equals("RACING")) checkDone(room);
            broadcast(room);
        }
    }

    private void checkDone(Room room) {
        if (room.players.values().stream().allMatch(p -> p.finished)) room.state = "DONE";
    }

    private void broadcast(Room room) {
        ObjectNode out = mapper.createObjectNode();
        out.put("type", "state").put("room", room.code).put("state", room.state);
        if (!room.state.equals("WAITING")) out.put("text", room.text);
        int len = room.text.length();
        double minutes = (System.currentTimeMillis() - room.startTime) / 60000.0;
        ArrayNode arr = out.putArray("players");
        for (Player p : room.players.values()) {
            double wpm = p.wpm;
            if (!p.finished && room.state.equals("RACING")) wpm = minutes > 0.0005 ? (p.pos / 5.0) / minutes : 0;
            arr.addObject().put("id", p.id).put("name", p.name)
               .put("progress", (int) Math.round(100.0 * p.pos / len))
               .put("wpm", (int) Math.round(wpm)).put("finished", p.finished).put("rank", p.rank);
        }
        room.players.values().forEach(p -> send(p.session, out));
    }

    private ObjectNode error(String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "error").put("message", message);
        return n;
    }

    private void send(WebSocketSession s, JsonNode node) {
        synchronized (s) { // a session can't send from two threads at once
            try { if (s.isOpen()) s.sendMessage(new TextMessage(node.toString())); }
            catch (IOException ignored) { }
        }
    }
}
