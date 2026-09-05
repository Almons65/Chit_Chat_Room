package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

public class Main {

    static final String DB_HOST = env("DB_HOST", "db");
    static final String DB_NAME = env("DB_NAME", "chat");
    static final String DB_USER = env("DB_USER", "postgres");
    static final String DB_PASS = env("DB_PASSWORD", "secret");
    static final String DB_URL  = "jdbc:postgresql://" + DB_HOST + ":5432/" + DB_NAME;

    static final List<String> ROOMS = List.of("general", "work-discussion", "random");
    
    static final Map<WsContext, String> activeSessions = new ConcurrentHashMap<>();

    static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    static void initDb() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            try (Connection conn = connect(); Statement st = conn.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id SERIAL PRIMARY KEY,
                        room VARCHAR(20) NOT NULL,
                        username VARCHAR(30) NOT NULL,
                        message VARCHAR(500) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                """);
                System.out.println("Database ready.");
                return;
            } catch (SQLException e) {
                System.out.println("DB not ready (attempt " + attempt + "/10): " + e.getMessage());
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Could not connect to database after 10 attempts.");
    }

    public record NewMessage(String room, String username, String message) {}

    public static void main(String[] args) {
        initDb();

        Javalin app = Javalin.create(cfg ->
            cfg.staticFiles.add("/public") 
        ).start(8080);

        app.ws("/chat", ws -> {
            ws.onConnect(ctx -> activeSessions.put(ctx, ctx.sessionId()));
            
            ws.onClose(ctx -> activeSessions.remove(ctx));
            
            ws.onMessage(ctx -> {
                NewMessage m = ctx.messageAsClass(NewMessage.class);
                saveAndBroadcast(m);
            });
        });

        app.get("/api/rooms", ctx -> ctx.json(ROOMS));

        app.get("/api/messages", ctx -> {
            String room = ctx.queryParam("room");
            if (room == null || !ROOMS.contains(room)) {
                ctx.status(400).json(Map.of("error", "invalid room"));
                return;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            try (Connection conn = connect();
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT id, username, message, created_at
                     FROM messages WHERE room = ?
                     ORDER BY id DESC LIMIT 100
                 """)) {
                ps.setString(1, room);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(Map.of(
                            "id", rs.getInt("id"),
                            "username", rs.getString("username"),
                            "message", rs.getString("message"),
                            "createdAt", rs.getTimestamp("created_at").toInstant().toString()
                        ));
                    }
                }
            }
            Collections.reverse(result);
            ctx.json(result);
        });

        app.post("/api/messages", ctx -> {
            NewMessage m = ctx.bodyAsClass(NewMessage.class);

            if (m.room() == null || !ROOMS.contains(m.room())) {
                ctx.status(400).json(Map.of("error", "invalid room")); return;
            }
            if (m.username() == null || m.username().isBlank() || m.username().length() > 30) {
                ctx.status(400).json(Map.of("error", "username must be 1-30 characters")); return;
            }
            if (m.message() == null || m.message().isBlank() || m.message().length() > 500) {
                ctx.status(400).json(Map.of("error", "message must be 1-500 characters")); return;
            }

            boolean success = saveAndBroadcast(m);
            if (success) {
                ctx.status(201).json(Map.of("status", "ok"));
            } else {
                ctx.status(500).json(Map.of("error", "Failed to save message"));
            }
        });

        System.out.println("Chat board running on http://localhost:8080");
    }

    private static boolean saveAndBroadcast(NewMessage m) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO messages (room, username, message) VALUES (?, ?, ?) RETURNING id, created_at")) {
            
            ps.setString(1, m.room());
            ps.setString(2, m.username().trim());
            ps.setString(3, m.message().trim());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> broadcastData = Map.of(
                        "id", rs.getInt("id"),
                        "room", m.room(),
                        "username", m.username().trim(),
                        "message", m.message().trim(),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()
                    );
                    
                    activeSessions.keySet().stream()
                        .filter(client -> client.session.isOpen())
                        .forEach(client -> client.send(broadcastData));
                    
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error during broadcast: " + e.getMessage());
        }
        return false;
    }
}