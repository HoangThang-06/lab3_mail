package client;

import model.Email;
import java.io.IOException;
import java.util.*;

public class POP3Client {
    public static boolean login(String host, String user, String pass) throws IOException {
        try (Conn c = new Conn(host, Config.POP3_PORT)) {
            c.read();
            c.cmd("USER " + user);
            boolean ok = c.cmd("PASS " + pass).startsWith("+OK");
            c.cmd("QUIT");
            return ok;
        }
    }

    /** Trả về thông báo của server (+OK ... hoặc -ERR ...). */
    public static String register(String host, String user, String pass) throws IOException {
        try (Conn c = new Conn(host, Config.POP3_PORT)) {
            c.read();
            String r = c.cmd("REGISTER " + user + " " + pass);
            c.cmd("QUIT");
            return r;
        }
    }

    /** Tải toàn bộ thư của folder INBOX hoặc SENT về. */
    public static List<Email> fetch(String host, String user, String pass, String folder) throws IOException {
        List<Email> list = new ArrayList<>();
        try (Conn c = new Conn(host, Config.POP3_PORT)) {
            c.read();
            Conn.expect(c.cmd("USER " + user), "+OK");
            Conn.expect(c.cmd("PASS " + pass), "+OK");
            Conn.expect(c.cmd("FOLDER " + folder), "+OK");
            int n = Integer.parseInt(Conn.expect(c.cmd("STAT"), "+OK").split(" ")[1]);
            for (int i = 1; i <= n; i++) {
                Conn.expect(c.cmd("RETR " + i), "+OK");
                Email e = new Email();
                StringBuilder body = new StringBuilder();
                boolean header = true;
                String l;
                while (!(l = c.read()).equals(".")) {
                    if (l.startsWith("..")) l = l.substring(1);
                    if (header) {
                        if (l.isEmpty()) { header = false; continue; }
                        int k = l.indexOf(':');
                        String key = l.substring(0, k), val = l.substring(k + 1).trim();
                        switch (key) {
                            case "From" -> e.sender = val;
                            case "To" -> e.receiver = val;
                            case "Subject" -> e.subject = val;
                            case "Date" -> e.sentAt = val;
                            case "X-Id" -> e.id = Integer.parseInt(val);
                            case "X-Read" -> e.read = Boolean.parseBoolean(val);
                        }
                    } else body.append(l).append('\n');
                }
                e.content = body.toString().replaceAll("\n+$", "");
                list.add(e);
            }
            c.cmd("QUIT");
        }
        return list;
    }
}
