package service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import model.Email;
import java.util.Properties;

/** Chuyển tiếp thư ra Gmail. Cần biến môi trường GMAIL_USER và GMAIL_APP_PASSWORD (App Password 16 ký tự). */
public class GmailService {
    public static void send(Email email) throws Exception {
        send(email.sender, email.receiver, email.subject, email.content);
    }

    public static void send(String from, String to, String subject, String body) throws Exception {
        String user = System.getenv("GMAIL_USER"), pass = System.getenv("GMAIL_APP_PASSWORD");
        if (user == null || pass == null) {
            throw new IllegalStateException("Chưa cấu hình GMAIL_USER / GMAIL_APP_PASSWORD");
        }

        String safeSubject = subject == null ? "" : subject;
        String safeBody = body == null ? "" : body;

        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.host", "smtp.gmail.com");
        p.put("mail.smtp.port", "587");

        Session s = Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        MimeMessage m = new MimeMessage(s);
        m.setFrom(new InternetAddress(user, "Mail System (" + from + ")", "UTF-8"));
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        m.setSubject(safeSubject, "UTF-8");
        m.setText("[Gửi từ " + from + "]\n\n" + safeBody, "UTF-8");
        Transport.send(m);
    }
}