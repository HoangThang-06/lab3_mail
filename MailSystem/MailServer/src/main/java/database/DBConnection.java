
package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    // Thông tin kết nối MySQL
    private static final String URL =
            "jdbc:mysql://localhost:3306/mail_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh";

    private static final String USER = "root";

    // Nhập mật khẩu MySQL của bạn tại đây
    private static final String PASS = "Thang#2006";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}

