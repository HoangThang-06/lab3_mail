package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainFrame extends JFrame {
    private final InboxPanel panel;

    public MainFrame(String host, String user, String pass) {
        super("Mail - " + user);
        panel = new InboxPanel(host, user, pass);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(0xF6F8FC));
        side.setBorder(new EmptyBorder(14, 10, 14, 10));

        JButton compose = btn("Soạn thư"), inbox = btn("Hộp thư đến"), sent = btn("Thư đã gửi"), refresh = btn("Làm mới");
        compose.setBackground(new Color(0xC2E7FF));
        compose.setFont(compose.getFont().deriveFont(Font.BOLD, 14f));
        for (JButton b : new JButton[]{compose, inbox, sent, refresh}) { side.add(b); side.add(Box.createVerticalStrut(8)); }

        compose.addActionListener(e -> new ComposeFrame(host, user, pass, panel).setVisible(true));
        inbox.addActionListener(e -> panel.load("INBOX"));
        sent.addActionListener(e -> panel.load("SENT"));
        refresh.addActionListener(e -> panel.reload());

        add(side, BorderLayout.WEST);
        add(panel, BorderLayout.CENTER);
        setSize(1000, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        panel.load("INBOX");
    }

    private static JButton btn(String t) {
        JButton b = new JButton(t);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(160, 38));
        b.setFocusPainted(false);
        return b;
    }
}
