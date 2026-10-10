package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Web Client (Swing): nhap URL -> goi WebFetch cua Java Server (GET <server>/fetch?url=...) -> hien source.
 * Logic mang nam o WebFetchService; lop nay chi lo giao dien. Moi lan fetch chay trong SwingWorker.
 * Chay: java -cp out client.WebClientApp [dia_chi_server]   (hoac run_webclient.bat)
 */
public class WebClientApp {
    private static final int MAX_HISTORY = 20;
    private static final Color OK_COLOR = new Color(0x1B, 0x6E, 0x2E);
    private static final Color ERROR_COLOR = new Color(0xB0, 0x1E, 0x1E);
    private static final String BASE_NOTE =
        "Luu y: server /fetch tu chen the <base href=\"...\"> vao <head> cua trang HTML, nen source nay chi khac ban goc dung 1 the do.";

    private final JFrame frame = new JFrame("Web Client - xem source qua WebFetch");
    private final JTextField serverField = new JTextField(22);
    private final JComboBox<String> urlCombo = new JComboBox<>();
    private final JCheckBox jsonCheck = new JCheckBox("format=json");
    private final JButton fetchButton = new JButton("Fetch");
    private final JButton saveButton = new JButton("Save...");
    private final JTextArea sourceArea = new JTextArea();
    private final JLabel noteLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("San sang. Nhap URL roi bam Fetch.");
    private final JTabbedPane tabs = new JTabbedPane();

    private String shownText = "";
    private String suggestedName = "source.txt";

    public WebClientApp(String serverBase) {
        serverField.setText(serverBase);
        urlCombo.setEditable(true);
        urlCombo.setSelectedItem(WebFetchService.DEFAULT_SERVER + "/index.html");
        jsonCheck.setToolTipText("Goi /fetch?format=json: tab Source hien metadata (status, contentType, contentLength, body)");

        sourceArea.setEditable(false);
        sourceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sourceArea.setTabSize(4);
        noteLabel.setForeground(new Color(0x8A, 0x5A, 0x00));
        noteLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel serverRow = new JPanel(new BorderLayout(6, 0));
        serverRow.add(new JLabel("Server WebFetch:"), BorderLayout.WEST);
        serverRow.add(serverField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(jsonCheck);
        buttons.add(fetchButton);
        buttons.add(saveButton);
        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.add(new JLabel("URL can xem:"), BorderLayout.WEST);
        urlRow.add(urlCombo, BorderLayout.CENTER);
        urlRow.add(buttons, BorderLayout.EAST);

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(serverRow);
        top.add(urlRow);

        JPanel sourcePanel = new JPanel(new BorderLayout());
        sourcePanel.add(new JScrollPane(sourceArea), BorderLayout.CENTER);
        sourcePanel.add(noteLabel, BorderLayout.SOUTH);
        tabs.addTab("Source", sourcePanel);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(top, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setSize(980, 660);
        frame.setLocationRelativeTo(null);

        fetchButton.addActionListener(e -> fetch(currentUrl(), null));
        saveButton.addActionListener(e -> save());
        urlCombo.addActionListener(e -> {
            if ("comboBoxEdited".equals(e.getActionCommand())) {
                fetch(currentUrl(), null);
            }
        });
    }

    public void open() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    void dispose() {
        frame.dispose();
    }

    void setJsonMode(boolean json) {
        jsonCheck.setSelected(json);
    }

    String sourceText() {
        return sourceArea.getText();
    }

    String statusText() {
        return statusLabel.getText();
    }

    String noteText() {
        return noteLabel.getText();
    }

    private String currentUrl() {
        Object item = urlCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    /** Goi tren EDT. onDone (co the null) chay tren EDT sau khi giao dien da cap nhat. */
    void fetch(String target, Runnable onDone) {
        if (!fetchButton.isEnabled()) {
            return;
        }
        fetchButton.setEnabled(false);
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Dang tai " + target + " ...");
        final boolean json = jsonCheck.isSelected();
        final String server = serverField.getText();
        new SwingWorker<WebFetchService.Result, Void>() {
            @Override
            protected WebFetchService.Result doInBackground() {
                return new WebFetchService(server).fetch(target, json);
            }

            @Override
            protected void done() {
                try {
                    display(get(), target);
                } catch (Exception ex) {
                    showFailure("Loi noi bo: " + ex);
                } finally {
                    fetchButton.setEnabled(true);
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }
        }.execute();
    }

    private void display(WebFetchService.Result r, String target) {
        if (!r.ok()) {
            showFailure(r.error());
            return;
        }
        shownText = r.jsonMode() ? r.rawResponse() : r.body();
        suggestedName = WebFetchService.suggestFileName(r);
        sourceArea.setLineWrap(r.jsonMode());
        sourceArea.setText(shownText.replace("\r\n", "\n"));
        sourceArea.setCaretPosition(0);
        noteLabel.setText(WebFetchService.isHtml(r.contentType()) ? BASE_NOTE : " ");
        String ct = r.contentType().isBlank() ? "(khong co)" : r.contentType();
        statusLabel.setForeground(r.status() >= 400 ? ERROR_COLOR : OK_COLOR);
        statusLabel.setText("Status: " + r.status() + "  |  Content-Type: " + ct
            + "  |  Length: " + r.contentLength() + " bytes  |  Mode: " + (r.jsonMode() ? "json" : "raw"));
        remember(target);
    }

    private void showFailure(String message) {
        shownText = "";
        suggestedName = "source.txt";
        sourceArea.setLineWrap(true);
        sourceArea.setText(message);
        sourceArea.setCaretPosition(0);
        noteLabel.setText(" ");
        statusLabel.setForeground(ERROR_COLOR);
        statusLabel.setText("Loi: " + message);
    }

    /** Lich su: moi nhat len dau, khong trung, toi da MAX_HISTORY muc. */
    private void remember(String url) {
        for (int i = urlCombo.getItemCount() - 1; i >= 0; i--) {
            if (url.equals(urlCombo.getItemAt(i))) {
                urlCombo.removeItemAt(i);
            }
        }
        urlCombo.insertItemAt(url, 0);
        while (urlCombo.getItemCount() > MAX_HISTORY) {
            urlCombo.removeItemAt(urlCombo.getItemCount() - 1);
        }
        urlCombo.setSelectedIndex(0);
    }

    private void save() {
        if (shownText.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Chua co noi dung de luu. Hay Fetch truoc.", "Save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(frame, file.getName() + " da ton tai. Ghi de?", "Save",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(file.toPath(), shownText, StandardCharsets.UTF_8);
            statusLabel.setForeground(OK_COLOR);
            statusLabel.setText("Da luu " + shownText.length() + " ky tu vao " + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Khong luu duoc file: " + ex, "Save", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        String server = args.length > 0 ? args[0] : WebFetchService.DEFAULT_SERVER;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // dung giao dien Swing mac dinh
            }
            new WebClientApp(server).open();
        });
    }
}
