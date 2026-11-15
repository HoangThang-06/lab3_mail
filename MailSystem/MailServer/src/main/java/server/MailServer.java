package server;

import javax.swing.*;

public class MailServer {
    public static final int SMTP_PORT = 2525, POP3_PORT = 1100;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new MailServerUI().setVisible(true));
    }
}