package server;

import model.Email;
import service.EmailService;
import service.GmailService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** SMTP tự viết bằng Socket: EHLO, AUTH PLAIN, MAIL FROM, RCPT TO, DATA, RSET, NOOP, QUIT */
public class SMTPServer implements Runnable {
    private final int port;
    private final EmailService svc = new EmailService();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public SMTPServer(int port) { this.port = port; }

    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[SMTP] Dang lang nghe cong " + port);
            while (true) { Socket s = ss.accept(); pool.submit(() -> handle(s)); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    static void reply(PrintWriter o, String l) { o.print(l + "\r\n"); o.flush(); }

    static String addr(String l) {
        int a = l.indexOf('<'), b = l.indexOf('>');
        String r = (a >= 0 && b > a) ? l.substring(a + 1, b) : l.substring(l.indexOf(':') + 1);
        return r.trim().toLowerCase();
    }

    private void handle(Socket s) {
        System.out.println("[SMTP] Ket noi tu " + s.getInetAddress());
        try (s;
             var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             var out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            String user = null, from = null, line;
            List<String> rcpts = new ArrayList<>();
            reply(out, "220 localhost SMTP ready");
            while ((line = in.readLine()) != null) {
                String u = line.toUpperCase();
                if (u.startsWith("HELO") || u.startsWith("EHLO")) {
                    reply(out, "250 localhost");
                } else if (u.startsWith("AUTH PLAIN")) {
                    try {
                        String[] p = new String(Base64.getDecoder().decode(line.substring(10).trim()), StandardCharsets.UTF_8).split("\0");
                        if (p.length == 3 && svc.authenticate(p[1], p[2])) { user = p[1].toLowerCase(); reply(out, "235 Authenticated"); }
                        else reply(out, "535 Authentication failed");
                    } catch (Exception e) { reply(out, "535 Authentication failed"); }
                } else if (u.startsWith("MAIL FROM")) {
                    if (user == null) { reply(out, "530 Authentication required"); continue; }
                    String f = addr(line);
                    if (!user.equals(f)) { reply(out, "553 Sender mismatch"); continue; }
                    from = f; rcpts.clear(); reply(out, "250 OK");
                } else if (u.startsWith("RCPT TO")) {
                    if (from == null) { reply(out, "503 MAIL FROM first"); continue; }
                    String r = addr(line);
                    if (!r.contains("@")) reply(out, "501 Bad address");
                    else if (r.endsWith(EmailService.LOCAL) && !svc.userExists(r)) reply(out, "550 No such user");
                    else { rcpts.add(r); reply(out, "250 OK"); }
                } else if (u.equals("DATA")) {
                    if (from == null || rcpts.isEmpty()) { reply(out, "503 Need MAIL and RCPT"); continue; }
                    reply(out, "354 End data with <CRLF>.<CRLF>");
                    String subject = "", l;
                    StringBuilder body = new StringBuilder();
                    boolean header = true;
                    while ((l = in.readLine()) != null && !l.equals(".")) {
                        if (l.startsWith("..")) l = l.substring(1);
                        if (header) {
                            if (l.isEmpty()) header = false;
                            else if (l.regionMatches(true, 0, "Subject:", 0, 8)) subject = l.substring(8).trim();
                        } else body.append(l).append('\n');
                    }
                    String text = body.toString().replaceAll("\n+$", "");
                    String err = null;
                    for (String r : rcpts) {
                        Email m = new Email();
                        m.sender = user; m.receiver = r; m.subject = subject; m.content = text;
                        svc.save(m);                                  // luôn lưu MySQL trước
                        if (!r.endsWith(EmailService.LOCAL)) {        // thư ngoài -> chuyển tiếp Gmail
                            try { GmailService.send(user, r, subject, text); }
                            catch (Exception e) { err = String.valueOf(e.getMessage()).replace('\n', ' '); }
                        }
                        System.out.println("[SMTP] " + user + " -> " + r);
                    }
                    reply(out, err == null ? "250 OK queued" : "554 Saved but Gmail relay failed: " + err);
                    from = null; rcpts.clear();
                } else if (u.equals("RSET")) { from = null; rcpts.clear(); reply(out, "250 OK");
                } else if (u.equals("NOOP")) { reply(out, "250 OK");
                } else if (u.equals("QUIT")) { reply(out, "221 Bye"); break;
                } else reply(out, "500 Unknown command");
            }
        } catch (Exception e) { System.out.println("[SMTP] " + e.getMessage()); }
    }
}
