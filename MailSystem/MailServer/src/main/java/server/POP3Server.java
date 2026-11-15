package server;

import model.Email;
import service.EmailService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** POP3 tự viết: USER, PASS, STAT, LIST, RETR, QUIT. Mở rộng: FOLDER INBOX|SENT, REGISTER email pass */
public class POP3Server implements Runnable {
    private final int port;
    private final EmailService svc = new EmailService();
    private ExecutorService pool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Consumer<String> logger = System.out::println;
    private Runnable onDataChanged;

    public POP3Server(int port) {
        this.port = port;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger != null ? logger : System.out::println;
    }

    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    public boolean isRunning() {
        return running;
    }

    private void log(String msg) {
        logger.accept("[POP3] " + msg);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        log("Da dung server POP3");
    }

    @Override
    public void run() {
        running = true;
        pool = Executors.newCachedThreadPool();
        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            log("Dang lang nghe cong " + port);
            while (running) {
                Socket s = ss.accept();
                pool.submit(() -> handle(s));
            }
        } catch (IOException e) {
            if (running) {
                log("Loi socket: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    static void reply(PrintWriter o, String l) {
        o.print(l + "\r\n");
        o.flush();
    }

    static int size(Email e) {
        int cLen = e.content != null ? e.content.getBytes(StandardCharsets.UTF_8).length : 0;
        int sLen = e.subject != null ? e.subject.getBytes(StandardCharsets.UTF_8).length : 0;
        return cLen + sLen;
    }

    private void handle(Socket s) {
        log("Ket noi tu " + s.getInetAddress().getHostAddress());
        try (s;
             var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             var out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            String user = null, line;
            boolean auth = false, inbox = true, done = false;
            List<Email> msgs = new ArrayList<>();
            reply(out, "+OK POP3 server ready");

            while (!done && (line = in.readLine()) != null) {
                String[] p = line.split(" ", 2);
                String cmd = p[0].toUpperCase(), arg = p.length > 1 ? p[1].trim() : "";
                if (!auth && !List.of("USER", "PASS", "REGISTER", "QUIT").contains(cmd)) {
                    reply(out, "-ERR login first");
                    continue;
                }
                switch (cmd) {
                    case "USER" -> {
                        user = arg.toLowerCase();
                        reply(out, "+OK");
                    }
                    case "PASS" -> {
                        if (user != null && svc.authenticate(user, arg)) {
                            auth = true;
                            inbox = true;
                            msgs = svc.list(user, false);
                            log("Dang nhap: " + user);
                            reply(out, "+OK logged in");
                        } else {
                            reply(out, "-ERR invalid login");
                        }
                    }
                    case "REGISTER" -> {
                        String[] r = arg.split(" ", 2);
                        if (r.length < 2 || r[0].length() > 100
                                || !r[0].toLowerCase().matches("[a-z0-9._-]+@localhost")
                                || r[1].length() < 3) {
                            reply(out, "-ERR email phai co dang ten@localhost (<=100 ky tu), mat khau >= 3 ky tu");
                        } else if (svc.register(r[0], r[1])) {
                            log("Dang ky tai khoan moi: " + r[0].toLowerCase());
                            if (onDataChanged != null) onDataChanged.run();
                            reply(out, "+OK registered");
                        } else {
                            reply(out, "-ERR email da ton tai");
                        }
                    }
                    case "FOLDER" -> {
                        inbox = !arg.equalsIgnoreCase("SENT");
                        msgs = svc.list(user, !inbox);
                        reply(out, "+OK " + (inbox ? "INBOX" : "SENT"));
                    }
                    case "STAT" -> reply(out, "+OK " + msgs.size() + " " + msgs.stream().mapToInt(POP3Server::size).sum());
                    case "LIST" -> {
                        reply(out, "+OK " + msgs.size() + " messages");
                        for (int i = 0; i < msgs.size(); i++) {
                            reply(out, (i + 1) + " " + size(msgs.get(i)));
                        }
                        reply(out, ".");
                    }
                    case "RETR" -> {
                        int i;
                        try { i = Integer.parseInt(arg); } catch (NumberFormatException e) { i = 0; }
                        if (i < 1 || i > msgs.size()) { reply(out, "-ERR no such message"); break; }
                        Email e = msgs.get(i - 1);
                        reply(out, "+OK");
                        reply(out, "From: " + e.sender);
                        reply(out, "To: " + e.receiver);
                        reply(out, "Subject: " + e.subject.replace("\r", "").replace("\n", " "));
                        reply(out, "Date: " + e.sentAt);
                        reply(out, "X-Id: " + e.id);
                        reply(out, "X-Read: " + e.read);
                        reply(out, "");
                        for (String l : e.content.split("\r?\n", -1)) {
                            reply(out, l.startsWith(".") ? "." + l : l);
                        }
                        reply(out, ".");
                        if (inbox && !e.read) {
                            svc.markRead(e.id);
                            e.read = true;
                            if (onDataChanged != null) onDataChanged.run();
                        }
                    }
                    case "QUIT" -> {
                        reply(out, "+OK bye");
                        done = true;
                    }
                    default -> reply(out, "-ERR unknown command");
                }
            }
        } catch (Exception e) {
            log(e.getMessage());
        }
    }
}