package service;

import database.DBConnection;
import model.Email;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

public class EmailService {
    public static final String LOCAL = "@localhost";

    public static String hash(String s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean authenticate(String email, String pw) throws SQLException {
        if (email == null || pw == null) return false;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email=? AND password=?")) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, hash(pw));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean userExists(String email) throws SQLException {
        if (email == null) return false;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email=?")) {
            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean register(String email, String pw) throws SQLException {
        if (email == null || pw == null) return false;
        String normalizedEmail = email.trim().toLowerCase();
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > 100 || pw.length() < 3) return false;
        if (userExists(normalizedEmail)) return false;

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(email,password) VALUES(?,?)")) {
            ps.setString(1, normalizedEmail);
            ps.setString(2, hash(pw));
            ps.executeUpdate();
            return true;
        }
    }

    public void save(Email m) throws SQLException {
        String sender = m.sender.trim().toLowerCase();
        String receiver = m.receiver.trim().toLowerCase();
        if (sender.length() > 100) sender = sender.substring(0, 100);
        if (receiver.length() > 100) receiver = receiver.substring(0, 100);

        String subject = m.subject;
        if (subject != null && subject.length() > 255) {
            subject = subject.substring(0, 255);
        }

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO emails(sender,receiver,subject,content,sent_at,is_read) VALUES(?,?,?,?,NOW(),0)")) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, subject);
            ps.setString(4, m.content);
            ps.executeUpdate();
        }
    }

    public List<Email> list(String user, boolean sent) throws SQLException {
        String col = sent ? "sender" : "receiver";
        List<Email> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, sender, receiver, subject, content, sent_at, is_read FROM emails WHERE " + col + "=? ORDER BY sent_at DESC, id DESC")) {
            ps.setString(1, user.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapEmail(rs));
                }
            }
        }
        return out;
    }

    public void markRead(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE emails SET is_read=1 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // --- Các phương thức phục vụ giao diện quản trị Server ---

    public List<String[]> getAllUsers() throws SQLException {
        List<String[]> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, email, password FROM users ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("email"),
                        rs.getString("password")
                });
            }
        }
        return list;
    }

    public boolean deleteUser(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(int id, String newPw) throws SQLException {
        if (newPw == null || newPw.length() < 3) return false;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE id=?")) {
            ps.setString(1, hash(newPw));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Email> getAllEmails() throws SQLException {
        List<Email> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, sender, receiver, subject, content, sent_at, is_read FROM emails ORDER BY sent_at DESC, id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapEmail(rs));
            }
        }
        return out;
    }

    public boolean deleteEmail(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM emails WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Email mapEmail(ResultSet rs) throws SQLException {
        Email e = new Email();
        e.id = rs.getInt("id");
        e.sender = rs.getString("sender");
        e.receiver = rs.getString("receiver");
        e.subject = rs.getString("subject") == null ? "" : rs.getString("subject");
        e.content = rs.getString("content") == null ? "" : rs.getString("content");
        Timestamp ts = rs.getTimestamp("sent_at");
        e.sentAt = ts != null ? ts.toString().substring(0, 19) : "";
        e.read = rs.getBoolean("is_read");
        return e;
    }
}