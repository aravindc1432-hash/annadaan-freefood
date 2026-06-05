package com.freefood.util;

import java.sql.*;

public class DatabaseUtil {
    private static final String DB_DIR  = System.getProperty("user.home") + "/freefood";
    private static final String DB_PATH = DB_DIR + "/freefood.db";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;
    public  static final String UPLOAD_DIR = DB_DIR + "/uploads";

    static {
        new java.io.File(DB_DIR).mkdirs();
        new java.io.File(UPLOAD_DIR).mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
            initSchema();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL);
        // Enable WAL for better concurrency
        try (Statement s = c.createStatement()) { s.execute("PRAGMA journal_mode=WAL"); }
        return c;
    }

    private static void initSchema() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    display_name  TEXT NOT NULL,
                    phone         TEXT,
                    is_admin      INTEGER DEFAULT 0,
                    created_at    TEXT DEFAULT (datetime('now','localtime'))
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS food_events (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id          INTEGER,
                    provider_name    TEXT NOT NULL,
                    contact_number   TEXT,
                    food_description TEXT NOT NULL,
                    address          TEXT NOT NULL,
                    city             TEXT NOT NULL,
                    state            TEXT NOT NULL,
                    latitude         REAL NOT NULL,
                    longitude        REAL NOT NULL,
                    event_date       TEXT NOT NULL,
                    start_time       TEXT NOT NULL,
                    end_time         TEXT NOT NULL,
                    quantity         INTEGER DEFAULT 0,
                    notes            TEXT,
                    photo_path       TEXT,
                    added_by_witness INTEGER DEFAULT 0,
                    witness_name     TEXT,
                    created_at       TEXT DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS going (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_id   INTEGER NOT NULL,
                    session_id TEXT NOT NULL,
                    user_name  TEXT,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    UNIQUE(event_id, session_id),
                    FOREIGN KEY (event_id) REFERENCES food_events(id) ON DELETE CASCADE
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS checkins (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_id     INTEGER NOT NULL,
                    checker_name TEXT,
                    session_id   TEXT NOT NULL,
                    latitude     REAL,
                    longitude    REAL,
                    note         TEXT,
                    created_at   TEXT DEFAULT (datetime('now','localtime')),
                    UNIQUE(event_id, session_id),
                    FOREIGN KEY (event_id) REFERENCES food_events(id) ON DELETE CASCADE
                )""");

            // Insert default admin if not exists
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username='admin'");
            if (rs.next() && rs.getInt(1) == 0) {
                String hash = org.mindrot.jbcrypt.BCrypt.hashpw("admin123", org.mindrot.jbcrypt.BCrypt.gensalt());
                stmt.execute("INSERT INTO users(username,password_hash,display_name,is_admin) VALUES('admin','" + hash + "','Administrator',1)");
                System.out.println("[DB] Default admin created: admin / admin123  ← CHANGE THIS!");
            }
            System.out.println("[DB] Schema OK. Path: " + DB_PATH);
        } catch (SQLException e) {
            throw new RuntimeException("Schema init failed", e);
        }
    }
}
