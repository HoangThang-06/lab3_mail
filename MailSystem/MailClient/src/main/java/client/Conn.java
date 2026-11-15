package client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Kết nối socket text theo dòng (dùng chung cho SMTP và POP3). */
class Conn implements Closeable {
    private final Socket s = new Socket();
    private final BufferedReader in;
    private final PrintWriter out;

    Conn(String host, int port) throws IOException {
        s.connect(new InetSocketAddress(host, port), 5000);
        s.setSoTimeout(30000);
        in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    void send(String l) { out.print(l + "\r\n"); out.flush(); }

    String read() throws IOException {
        String l = in.readLine();
        if (l == null) throw new IOException("Server đã đóng kết nối");
        return l;
    }

    String cmd(String l) throws IOException { send(l); return read(); }

    static String expect(String resp, String prefix) throws IOException {
        if (!resp.startsWith(prefix)) throw new IOException(resp);
        return resp;
    }

    public void close() throws IOException { s.close(); }
}
