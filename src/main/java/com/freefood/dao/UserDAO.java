package com.freefood.dao;

import com.freefood.model.User;
import com.freefood.util.DatabaseUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public long register(String username, String password, String displayName, String phone) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql  = "INSERT INTO users(username,password_hash,display_name,phone) VALUES(?,?,?,?)";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim().toLowerCase());
            ps.setString(2, hash);
            ps.setString(3, displayName.trim());
            ps.setString(4, phone);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) return k.getLong(1); }
        }
        return -1;
    }

    public User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username=?";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (BCrypt.checkpw(password, hash)) return mapRow(rs);
                }
            }
        }
        return null;
    }

    public User getById(long id) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapRow(rs); }
        }
        return null;
    }

    public boolean usernameExists(String username) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public boolean deleteUser(long id) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=? AND is_admin=0")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean changePassword(long userId, String newPassword) throws SQLException {
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
            ps.setString(1, hash);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setDisplayName(rs.getString("display_name"));
        u.setPhone(rs.getString("phone"));
        u.setAdmin(rs.getInt("is_admin") == 1);
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}
