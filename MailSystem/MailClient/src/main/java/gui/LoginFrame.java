package gui;

import client.POP3Client;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final JTextField host = new JTextField("localhost"), email = new JTextField("thang@localhost");
    private final JPasswordField pass = new JPasswordField();

    public LoginFrame() {
        super("Mail Client - Đăng nhập");
        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.setBorder(new EmptyBorder(20, 24, 20, 24));
        JLabel t = new JLabel("Mail System", SwingConstants.CENTER);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 22f));
        JButton login = new JButton("Đăng nhập"), reg = new JButton("Đăng ký");
        JPanel b = new JPanel(new GridLayout(1, 2, 8, 0));
        b.add(login); b.add(reg);
        p.add(t);
        p.add(new JLabel("IP Mail Server (localhost hoặc IP máy chủ trong LAN)")); p.add(host);
        p.add(new JLabel("Email")); p.add(email);
        p.add(new JLabel("Mật khẩu")); p.add(pass);
        p.add(b);
        add(p);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setSize(400, getHeight());
        setLocationRelativeTo(null);
        getRootPane().setDefaultButton(login);
        login.addActionListener(e -> login());
        reg.addActionListener(e -> register());
    }

    static String err(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }

    private void login() {
        String h = host.getText().trim(), u = email.getText().trim().toLowerCase(), pw = new String(pass.getPassword());
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() throws Exception { return POP3Client.login(h, u, pw); }
            protected void done() {
                try {
                    if (get()) { new MainFrame(h, u, pw).setVisible(true); dispose(); }
                    else JOptionPane.showMessageDialog(LoginFrame.this, "Sai email hoặc mật khẩu");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LoginFrame.this, "Không kết nối được server: " + err(ex));
                }
            }
        }.execute();
    }

    private void register() {
        String h = host.getText().trim(), u = email.getText().trim().toLowerCase(), pw = new String(pass.getPassword());
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception { return POP3Client.register(h, u, pw); }
            protected void done() {
                try {
                    String r = get();
                    JOptionPane.showMessageDialog(LoginFrame.this, r.startsWith("+OK") ? "Đăng ký thành công, hãy đăng nhập" : r);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LoginFrame.this, "Không kết nối được server: " + err(ex));
                }
            }
        }.execute();
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
