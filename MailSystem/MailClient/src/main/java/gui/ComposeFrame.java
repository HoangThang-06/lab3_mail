package gui;

import client.SMTPClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ComposeFrame extends JFrame {
    private final JTextField to = new JTextField(), subject = new JTextField();
    private final JTextArea body = new JTextArea();
    private final JButton send = new JButton("Gửi");

    public ComposeFrame(String host, String user, String pass, InboxPanel panel) {
        super("Thư mới");
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 6));
        top.setBorder(new EmptyBorder(10, 10, 6, 10));
        top.add(new JLabel("Đến (ngăn cách bằng dấu phẩy):")); top.add(to);
        top.add(new JLabel("Tiêu đề:")); top.add(subject);
        body.setLineWrap(true);
        body.setMargin(new Insets(8, 10, 8, 10));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        send.setBackground(new Color(0x0B57D0));
        send.setForeground(Color.WHITE);
        bottom.add(send);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(body), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setSize(560, 460);
        setLocationRelativeTo(null);

        send.addActionListener(e -> {
            String t = to.getText().trim();
            if (t.isEmpty()) { JOptionPane.showMessageDialog(this, "Chưa nhập người nhận"); return; }
            send.setEnabled(false);
            send.setText("Đang gửi...");
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    SMTPClient.send(host, user, pass, t, subject.getText().trim(), body.getText());
                    return null;
                }
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(ComposeFrame.this, "Đã gửi thư");
                        dispose();
                        panel.reload();
                    } catch (Exception ex) {
                        send.setEnabled(true);
                        send.setText("Gửi");
                        JOptionPane.showMessageDialog(ComposeFrame.this, "Gửi thất bại: " + LoginFrame.err(ex));
                    }
                }
            }.execute();
        });
    }
}
