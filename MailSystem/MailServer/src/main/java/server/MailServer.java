package server;

import database.DBConnection;

public class MailServer {
    public static final int SMTP_PORT = 2525, POP3_PORT = 1100;

    public static void main(String[] args) throws Exception {
        try (var c = DBConnection.getConnection()) { System.out.println("[DB] Ket noi MySQL thanh cong"); }
        new Thread(new SMTPServer(SMTP_PORT), "smtp").start();
        new Thread(new POP3Server(POP3_PORT), "pop3").start();
        System.out.println("Mail Server dang chay. Domain noi bo: @localhost");
    }
}
