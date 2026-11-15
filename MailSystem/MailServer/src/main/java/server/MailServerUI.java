package server;

import database.DBConnection;
import model.Email;
import service.EmailService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MailServerUI extends JFrame {
    private final EmailService emailService = new EmailService();
    private SMTPServer smtpServer;
    private POP3Server pop3Server;

    // Status labels
    private final JLabel lblDbStatus = new JLabel("MySQL: Đang kiểm tra...");
    private final JLabel lblSmtpStatus = new JLabel("SMTP (:2525): Đang dừng");
    private final JLabel lblPop3Status = new JLabel("POP3 (:1100): Đang dừng");
    private final JLabel lblGmailStatus = new JLabel();

    // KPI labels
    private final JLabel lblUserCount = new JLabel("0");
    private final JLabel lblEmailCount = new JLabel("0");
    private final JLabel lblUnreadCount = new JLabel("0");

    // Control buttons
    private final JButton btnStart = new JButton("Khởi động Server");
    private final JButton btnStop = new JButton("Dừng Server");
    private final JButton btnRefresh = new JButton("Làm mới dữ liệu");

    // Logs tab
    private final JTextArea txtLogs = new JTextArea();
    private final JComboBox<String> cboLogFilter = new JComboBox<>(new String[]{"Tất cả", "[SMTP]", "[POP3]", "[DB]", "[SYSTEM]"});
    private final JCheckBox chkAutoScroll = new JCheckBox("Tự động cuộn", true);
    private final List<String> logHistory = new ArrayList<>();

    // Users tab
    private DefaultTableModel userModel;
    private JTable tblUsers;
    private final JTextField txtNewEmail = new JTextField(18);
    private final JPasswordField txtNewPass = new JPasswordField(14);

    // Emails tab
    private DefaultTableModel emailModel;
    private JTable tblEmails;
    private final JTextField txtSearchEmail = new JTextField(22);
    private final JTextArea txtEmailContent = new JTextArea();
    private List<Email> cachedEmails = new ArrayList<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MailServerUI() {
        setTitle("Mail Server Console - Domain @localhost");
        setSize(1020, 680);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();
        checkDatabaseConnection();
        refreshAllData();
        startServers();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(245, 247, 250));
        setContentPane(root);

        // --- TOP PANEL: Trạng thái & Điều khiển ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setOpaque(false);

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 232)));

        Font statusFont = new Font("SansSerif", Font.BOLD, 13);
        lblDbStatus.setFont(statusFont);
        lblSmtpStatus.setFont(statusFont);
        lblPop3Status.setFont(statusFont);
        lblGmailStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));

        boolean hasGmail = System.getenv("GMAIL_USER") != null && System.getenv("GMAIL_APP_PASSWORD") != null;
        lblGmailStatus.setText(hasGmail ? "Gmail Relay: Sẵn sàng" : "Gmail Relay: Chưa cấu hình ENV");
        lblGmailStatus.setForeground(hasGmail ? new Color(22, 130, 65) : new Color(160, 110, 20));

        statusBar.add(lblDbStatus);
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(lblSmtpStatus);
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(lblPop3Status);
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(lblGmailStatus);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        controlBar.setOpaque(false);

        styleButton(btnStart, new Color(25, 135, 84), Color.WHITE);
        styleButton(btnStop, new Color(220, 53, 69), Color.WHITE);
        styleButton(btnRefresh, new Color(13, 110, 253), Color.WHITE);

        btnStart.addActionListener(e -> startServers());
        btnStop.addActionListener(e -> stopServers());
        btnRefresh.addActionListener(e -> {
            checkDatabaseConnection();
            refreshAllData();
            appendLog("[SYSTEM] Da lam moi du lieu tu MySQL");
        });

        controlBar.add(btnStart);
        controlBar.add(btnStop);
        controlBar.add(btnRefresh);

        // KPI Cards
        JPanel kpiPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        kpiPanel.setOpaque(false);
        kpiPanel.add(createKpiCard("Tài khoản (@localhost)", lblUserCount, new Color(13, 110, 253)));
        kpiPanel.add(createKpiCard("Tổng số Email", lblEmailCount, new Color(25, 135, 84)));
        kpiPanel.add(createKpiCard("Email chưa đọc", lblUnreadCount, new Color(214, 122, 18)));

        JPanel headerWrapper = new JPanel(new BorderLayout(8, 8));
        headerWrapper.setOpaque(false);
        headerWrapper.add(statusBar, BorderLayout.CENTER);
        headerWrapper.add(controlBar, BorderLayout.EAST);

        topPanel.add(headerWrapper, BorderLayout.NORTH);
        topPanel.add(kpiPanel, BorderLayout.CENTER);
        root.add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Tabs ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Nhật ký Server (Logs)", buildLogsTab());
        tabs.addTab("Quản lý Tài khoản (Users)", buildUsersTab());
        tabs.addTab("Giám sát Email (Emails)", buildEmailsTab());

        root.add(tabs, BorderLayout.CENTER);
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                new EmptyBorder(10, 14, 10, 14)
        ));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblTitle.setForeground(new Color(100, 110, 125));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        valueLabel.setForeground(new Color(33, 37, 41));

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLogsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.add(new JLabel("Bộ lọc:"));
        toolbar.add(cboLogFilter);
        toolbar.add(chkAutoScroll);

        JButton btnClearLog = new JButton("Xóa nhật ký");
        btnClearLog.addActionListener(e -> {
            logHistory.clear();
            txtLogs.setText("");
        });
        toolbar.add(btnClearLog);

        cboLogFilter.addActionListener(e -> renderFilteredLogs());

        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtLogs.setBackground(new Color(24, 26, 32));
        txtLogs.setForeground(new Color(224, 230, 240));
        txtLogs.setMargin(new Insets(8, 10, 8, 10));

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildUsersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Form tạo user
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        form.setBorder(new TitledBorder("Thêm tài khoản mới"));
        form.add(new JLabel("Email:"));
        form.add(txtNewEmail);
        form.add(new JLabel("Mật khẩu:"));
        form.add(txtNewPass);

        JButton btnAddUser = new JButton("Tạo tài khoản");
        styleButton(btnAddUser, new Color(25, 135, 84), Color.WHITE);
        btnAddUser.addActionListener(e -> handleCreateUser());
        form.add(btnAddUser);

        userModel = new DefaultTableModel(new String[]{"ID", "Email", "Mật khẩu (SHA-256)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblUsers = new JTable(userModel);
        tblUsers.setRowHeight(26);
        tblUsers.getColumnModel().getColumn(0).setMaxWidth(60);
        tblUsers.getColumnModel().getColumn(1).setPreferredWidth(220);
        tblUsers.getColumnModel().getColumn(2).setPreferredWidth(460);

        // Thanh hành động bên dưới
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton btnResetPw = new JButton("Đổi mật khẩu");
        JButton btnDeleteUser = new JButton("Xóa tài khoản");
        styleButton(btnDeleteUser, new Color(220, 53, 69), Color.WHITE);

        btnResetPw.addActionListener(e -> handleResetPassword());
        btnDeleteUser.addActionListener(e -> handleDeleteUser());

        bottom.add(btnResetPw);
        bottom.add(btnDeleteUser);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(tblUsers), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildEmailsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchBar.add(new JLabel("Tìm kiếm (Người gửi / Người nhận / Tiêu đề):"));
        searchBar.add(txtSearchEmail);
        txtSearchEmail.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterEmailsTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterEmailsTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterEmailsTable(); }
        });

        JButton btnDeleteEmail = new JButton("Xóa Email đã chọn");
        styleButton(btnDeleteEmail, new Color(220, 53, 69), Color.WHITE);
        btnDeleteEmail.addActionListener(e -> handleDeleteEmail());
        searchBar.add(btnDeleteEmail);

        emailModel = new DefaultTableModel(new String[]{"ID", "Người gửi", "Người nhận", "Tiêu đề", "Thời gian gửi", "Trạng thái"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblEmails = new JTable(emailModel);
        tblEmails.setRowHeight(26);
        tblEmails.getColumnModel().getColumn(0).setMaxWidth(55);
        tblEmails.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedEmailContent();
        });

        txtEmailContent.setEditable(false);
        txtEmailContent.setFont(new Font("SansSerif", Font.PLAIN, 13));
        txtEmailContent.setLineWrap(true);
        txtEmailContent.setWrapStyleWord(true);
        txtEmailContent.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane contentScroll = new JScrollPane(txtEmailContent);
        contentScroll.setBorder(new TitledBorder("Nội dung chi tiết Email"));
        contentScroll.setPreferredSize(new Dimension(0, 180));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tblEmails), contentScroll);
        splitPane.setResizeWeight(0.62);

        panel.add(searchBar, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
    }

    // --- Xử lý Server & Database ---

    private void checkDatabaseConnection() {
        try (Connection c = DBConnection.getConnection()) {
            lblDbStatus.setText("MySQL (mail_system): Đã kết nối");
            lblDbStatus.setForeground(new Color(22, 130, 65));
            appendLog("[DB] Ket noi MySQL mail_system thanh cong");
        } catch (Exception ex) {
            lblDbStatus.setText("MySQL: Lỗi kết nối!");
            lblDbStatus.setForeground(new Color(200, 35, 51));
            appendLog("[DB] Loi ket noi MySQL: " + ex.getMessage());
        }
    }

    public void startServers() {
        if (smtpServer == null || !smtpServer.isRunning()) {
            smtpServer = new SMTPServer(MailServer.SMTP_PORT);
            smtpServer.setLogger(this::appendLog);
            smtpServer.setOnDataChanged(() -> SwingUtilities.invokeLater(this::refreshAllData));
            new Thread(smtpServer, "smtp-server").start();
        }
        if (pop3Server == null || !pop3Server.isRunning()) {
            pop3Server = new POP3Server(MailServer.POP3_PORT);
            pop3Server.setLogger(this::appendLog);
            pop3Server.setOnDataChanged(() -> SwingUtilities.invokeLater(this::refreshAllData));
            new Thread(pop3Server, "pop3-server").start();
        }
        updateServerStatusLabels(true);
    }

    public void stopServers() {
        if (smtpServer != null && smtpServer.isRunning()) smtpServer.stop();
        if (pop3Server != null && pop3Server.isRunning()) pop3Server.stop();
        updateServerStatusLabels(false);
    }

    private void updateServerStatusLabels(boolean running) {
        if (running) {
            lblSmtpStatus.setText("SMTP (:" + MailServer.SMTP_PORT + "): Đang chạy");
            lblSmtpStatus.setForeground(new Color(22, 130, 65));
            lblPop3Status.setText("POP3 (:" + MailServer.POP3_PORT + "): Đang chạy");
            lblPop3Status.setForeground(new Color(22, 130, 65));
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            lblSmtpStatus.setText("SMTP (:" + MailServer.SMTP_PORT + "): Đã dừng");
            lblSmtpStatus.setForeground(new Color(200, 35, 51));
            lblPop3Status.setText("POP3 (:" + MailServer.POP3_PORT + "): Đã dừng");
            lblPop3Status.setForeground(new Color(200, 35, 51));
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }

    public void appendLog(String message) {
        String entry = "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
        System.out.println(entry);
        SwingUtilities.invokeLater(() -> {
            logHistory.add(entry);
            String filter = (String) cboLogFilter.getSelectedItem();
            if ("Tất cả".equals(filter) || (filter != null && entry.contains(filter))) {
                txtLogs.append(entry + "\n");
                if (chkAutoScroll.isSelected()) {
                    txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
                }
            }
        });
    }

    private void renderFilteredLogs() {
        String filter = (String) cboLogFilter.getSelectedItem();
        StringBuilder sb = new StringBuilder();
        for (String line : logHistory) {
            if ("Tất cả".equals(filter) || (filter != null && line.contains(filter))) {
                sb.append(line).append('\n');
            }
        }
        txtLogs.setText(sb.toString());
    }

    public void refreshAllData() {
        try {
            // Load Users
            List<String[]> users = emailService.getAllUsers();
            userModel.setRowCount(0);
            for (String[] u : users) {
                userModel.addRow(u);
            }
            lblUserCount.setText(String.valueOf(users.size()));

            // Load Emails
            cachedEmails = emailService.getAllEmails();
            long unread = cachedEmails.stream().filter(m -> !m.read).count();
            lblEmailCount.setText(String.valueOf(cachedEmails.size()));
            lblUnreadCount.setText(String.valueOf(unread));
            filterEmailsTable();
        } catch (Exception ex) {
            appendLog("[DB] Loi tai du lieu: " + ex.getMessage());
        }
    }

    private void filterEmailsTable() {
        String kw = txtSearchEmail.getText().trim().toLowerCase();
        emailModel.setRowCount(0);
        for (Email e : cachedEmails) {
            if (kw.isEmpty()
                    || e.sender.toLowerCase().contains(kw)
                    || e.receiver.toLowerCase().contains(kw)
                    || e.subject.toLowerCase().contains(kw)) {
                emailModel.addRow(new Object[]{
                        e.id,
                        e.sender,
                        e.receiver,
                        e.subject,
                        e.sentAt,
                        e.read ? "Đã đọc" : "Chưa đọc"
                });
            }
        }
    }

    private void showSelectedEmailContent() {
        int row = tblEmails.getSelectedRow();
        if (row < 0) {
            txtEmailContent.setText("");
            return;
        }
        int id = (int) emailModel.getValueAt(row, 0);
        for (Email e : cachedEmails) {
            if (e.id == id) {
                txtEmailContent.setText(
                        "Từ: " + e.sender + "\n" +
                                "Đến: " + e.receiver + "\n" +
                                "Tiêu đề: " + e.subject + "\n" +
                                "Thời gian: " + e.sentAt + "\n" +
                                "--------------------------------------------------\n" +
                                e.content
                );
                txtEmailContent.setCaretPosition(0);
                break;
            }
        }
    }

    // --- Thao tác CRUD trên UI ---

    private void handleCreateUser() {
        String email = txtNewEmail.getText().trim().toLowerCase();
        String pw = new String(txtNewPass.getPassword());
        if (email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập email!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!email.contains("@")) {
            email += EmailService.LOCAL;
        }
        if (!email.matches("[a-z0-9._-]+@localhost") || email.length() > 100) {
            JOptionPane.showMessageDialog(this, "Email phải có định dạng ten@localhost (tối đa 100 ký tự).", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (pw.length() < 3) {
            JOptionPane.showMessageDialog(this, "Mật khẩu phải có ít nhất 3 ký tự.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (emailService.register(email, pw)) {
                appendLog("[DB] Admin da tao tai khoan: " + email);
                txtNewEmail.setText("");
                txtNewPass.setText("");
                refreshAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Email đã tồn tại trong hệ thống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi database: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleResetPassword() {
        int row = tblUsers.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tài khoản cần đổi mật khẩu.");
            return;
        }
        int id = Integer.parseInt(userModel.getValueAt(row, 0).toString());
        String email = userModel.getValueAt(row, 1).toString();
        String newPw = JOptionPane.showInputDialog(this, "Nhập mật khẩu mới cho " + email + " (>= 3 ký tự):");
        if (newPw == null) return;
        if (newPw.length() < 3) {
            JOptionPane.showMessageDialog(this, "Mật khẩu phải từ 3 ký tự trở lên.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (emailService.updatePassword(id, newPw)) {
                appendLog("[DB] Da cap nhat mat khau cho: " + email);
                refreshAllData();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi cập nhật: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeleteUser() {
        int row = tblUsers.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tài khoản cần xóa.");
            return;
        }
        int id = Integer.parseInt(userModel.getValueAt(row, 0).toString());
        String email = userModel.getValueAt(row, 1).toString();
        int confirm = JOptionPane.showConfirmDialog(this, "Xóa tài khoản " + email + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            if (emailService.deleteUser(id)) {
                appendLog("[DB] Da xoa tai khoan: " + email);
                refreshAllData();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi xóa tài khoản: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeleteEmail() {
        int row = tblEmails.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn email cần xóa.");
            return;
        }
        int id = (int) emailModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Xóa email ID #" + id + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            if (emailService.deleteEmail(id)) {
                appendLog("[DB] Da xoa email ID #" + id);
                txtEmailContent.setText("");
                refreshAllData();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi xóa email: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}