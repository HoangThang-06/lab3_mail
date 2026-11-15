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
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public boolean authenticate(String email, String pw) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email=? AND password=?")) {
            ps.setString(1, email.toLowerCase());
            ps.setString(2, hash(pw));
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean userExists(String email) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email=?")) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean register(String email, String pw) throws SQLException {
        if (userExists(email)) return false;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(email,password) VALUES(?,?)")) {
            ps.setString(1, email.toLowerCase());
            ps.setString(2, hash(pw));
            ps.executeUpdate();
            return true;
        }
    }

    public void save(Email m) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO emails(sender,receiver,subject,content,sent_at,is_read) VALUES(?,?,?,?,NOW(),0)")) {
            ps.setString(1, m.sender);
            ps.setString(2, m.receiver);
            ps.setString(3, m.subject);
            ps.setString(4, m.content);
            ps.executeUpdate();
        }
    }

    public List<Email> list(String user, boolean sent) throws SQLException {
        String col = sent ? "sender" : "receiver";
        List<Email> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM emails WHERE " + col + "=? ORDER BY sent_at DESC, id DESC")) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Email e = new Email();
                    e.id = rs.getInt("id");
                    e.sender = rs.getString("sender");
                    e.receiver = rs.getString("receiver");
                    e.subject = rs.getString("subject") == null ? "" : rs.getString("subject");
                    e.content = rs.getString("content") == null ? "" : rs.getString("content");
                    e.sentAt = rs.getTimestamp("sent_at").toString().substring(0, 19);
                    e.read = rs.getBoolean("is_read");
                    out.add(e);
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
}
