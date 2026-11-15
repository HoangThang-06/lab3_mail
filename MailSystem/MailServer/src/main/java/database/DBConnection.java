package database;

import java.sql.*;

public class DBConnection {
    // Có thể đổi bằng biến môi trường DB_URL, DB_USER, DB_PASS
    static final String URL = env("DB_URL", "jdbc:mysql://localhost:3306/mail_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
    static final String USER = env("DB_USER", "root");
    static final String PASS = env("DB_PASS", "");

    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
