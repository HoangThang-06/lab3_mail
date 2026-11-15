package gui;

import client.POP3Client;
import model.Email;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Danh sách thư (Inbox / Sent) + khung đọc nội dung thư. */
public class InboxPanel extends JPanel {
    private final String host, user, pass;
    private String folder = "INBOX";
    private List<Email> emails = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"", "Tiêu đề", "Thời gian"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextArea view = new JTextArea();
    private final JLabel title = new JLabel();

    public InboxPanel(String host, String user, String pass) {
        super(new BorderLayout());
        this.host = host; this.user = user; this.pass = pass;

        title.setBorder(new EmptyBorder(10, 12, 10, 12));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                boolean unread = folder.equals("INBOX") && r < emails.size() && !emails.get(r).read;
                comp.setFont(comp.getFont().deriveFont(unread ? Font.BOLD : Font.PLAIN));
                return comp;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (e.getValueIsAdjusting() || r < 0 || r >= emails.size()) return;
            Email m = emails.get(r);
            m.read = true;
            view.setText("Từ: " + m.sender + "\nĐến: " + m.receiver + "\nThời gian: " + m.sentAt
                    + "\nTiêu đề: " + m.subject + "\n" + "-".repeat(60) + "\n\n" + m.content);
            view.setCaretPosition(0);
            table.repaint();
        });

        view.setEditable(false);
        view.setLineWrap(true);
        view.setWrapStyleWord(true);
        view.setMargin(new Insets(10, 12, 10, 12));
        view.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(view));
        split.setResizeWeight(0.45);
        add(title, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    public void reload() { load(folder); }

    public void load(String f) {
        folder = f;
        title.setText(f.equals("INBOX") ? "Hộp thư đến" : "Thư đã gửi");
        model.setRowCount(0);
        view.setText("Đang tải...");
        new SwingWorker<List<Email>, Void>() {
            protected List<Email> doInBackground() throws Exception { return POP3Client.fetch(host, user, pass, f); }
            protected void done() {
                try {
                    emails = get();
                    model.setRowCount(0);
                    table.getColumnModel().getColumn(0).setHeaderValue(f.equals("INBOX") ? "Người gửi" : "Người nhận");
                    for (Email m : emails)
                        model.addRow(new Object[]{f.equals("INBOX") ? m.sender : m.receiver, m.subject, m.sentAt});
                    table.getTableHeader().repaint();
                    view.setText(emails.isEmpty() ? "Không có thư." : "");
                } catch (Exception ex) {
                    view.setText("Lỗi tải thư: " + LoginFrame.err(ex));
                }
            }
        }.execute();
    }
}
