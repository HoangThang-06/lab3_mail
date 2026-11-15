package client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SMTPClient {
    /** Gửi thư qua SMTP. "to" có thể nhiều địa chỉ, ngăn cách bởi , hoặc ; */
    public static void send(String host, String user, String pass, String to, String subject, String body) throws IOException {
        try (Conn c = new Conn(host, Config.SMTP_PORT)) {
            Conn.expect(c.read(), "220");
            Conn.expect(c.cmd("EHLO client"), "250");
            String cred = Base64.getEncoder().encodeToString(("\0" + user + "\0" + pass).getBytes(StandardCharsets.UTF_8));
            Conn.expect(c.cmd("AUTH PLAIN " + cred), "235");
            Conn.expect(c.cmd("MAIL FROM:<" + user + ">"), "250");
            for (String r : to.split("[,;]")) if (!r.isBlank()) Conn.expect(c.cmd("RCPT TO:<" + r.trim() + ">"), "250");
            Conn.expect(c.cmd("DATA"), "354");
            c.send("From: " + user);
            c.send("To: " + to);
            c.send("Subject: " + subject.replace("\r", " ").replace("\n", " "));
            c.send("");
            for (String l : body.split("\r?\n", -1)) c.send(l.startsWith(".") ? "." + l : l);
            c.send(".");
            Conn.expect(c.read(), "250");
            c.cmd("QUIT");
        }
    }
}
