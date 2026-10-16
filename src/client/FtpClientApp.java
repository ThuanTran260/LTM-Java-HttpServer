package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Swing-based Dual-Pane FTP Client Explorer (RFC 959).
 * Features:
 * - Real-time RFC 959 command and response console with password sanitization
 * - Quick presets (Java FTP Server :2121, Windows IIS FTP :21, Anonymous :2121)
 * - Dual-pane file browser (Local filesystem on Left, Remote FTP on Right)
 * - Off-EDT asynchronous socket operations using JDK 24 Virtual Threads
 * - Full file operations: Upload, Download, Delete, New Folder, Directory Navigation
 */
public class FtpClientApp extends JFrame {

    private static final Color COLOR_CONNECTED = new Color(0x1B, 0x8E, 0x2E);
    private static final Color COLOR_DISCONNECTED = new Color(0xB0, 0x1E, 0x1E);

    private final FtpClient ftpClient = new FtpClient();

    // Top Connection Components
    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("2121", 4);
    private final JTextField userField = new JTextField("admin", 8);
    private final JPasswordField passField = new JPasswordField("admin123", 8);
    private final JComboBox<String> modeCombo = new JComboBox<>(new String[]{"Passive (PASV)", "Active (PORT)"});
    private final JButton connectBtn = new JButton("Connect");
    private final JLabel statusDot = new JLabel("● Disconnected");

    // Local Pane Components
    private final JTextField localPathField = new JTextField();
    private final JButton localUpBtn = new JButton("Up [..]");
    private final JButton localBrowseBtn = new JButton("Browse...");
    private final JButton localRefreshBtn = new JButton("Refresh");
    private final DefaultTableModel localTableModel = new DefaultTableModel(
            new Object[]{"Name", "Size", "Type", "Modified Date"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable localTable = new JTable(localTableModel);
    private File currentLocalDir;

    // Remote Pane Components
    private final JTextField remotePathField = new JTextField("/");
    private final JButton remoteUpBtn = new JButton("Up [..]");
    private final JButton remoteRefreshBtn = new JButton("Refresh");
    private final DefaultTableModel remoteTableModel = new DefaultTableModel(
            new Object[]{"Name", "Size", "Type", "Modified Date"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable remoteTable = new JTable(remoteTableModel);
    private List<FtpFile> currentRemoteFiles = List.of();

    // Action Toolbar Components
    private final JButton uploadBtn = new JButton("Upload ===>>>");
    private final JButton downloadBtn = new JButton("<<<=== Download");
    private final JButton deleteRemoteBtn = new JButton("Delete Remote");
    private final JButton newFolderRemoteBtn = new JButton("New Remote Folder...");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel("Ready.");

    // Bottom Console Components
    private final JTextArea consoleArea = new JTextArea(8, 80);
    private final JButton clearLogBtn = new JButton("Clear Console");

    public FtpClientApp() {
        super("Pure Java FTP Client (RFC 959 Dual-Pane Explorer)");
        initUI();
        initListeners();
        setInitialState();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(4, 4));

        // 1. Top Panel (Presets + Connection Form)
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        // Row 1: Presets
        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        presetRow.add(new JLabel("Presets:"));
        JButton presetJava = new JButton("⚡ Java FTP (:2121)");
        presetJava.setToolTipText("Connect to SimpleFtpServer on 127.0.0.1:2121 (admin/admin123)");
        presetJava.addActionListener(e -> applyPreset("127.0.0.1", "2121", "admin", "admin123", true));

        JButton presetIis = new JButton("🪟 Windows IIS FTP (:21)");
        presetIis.setToolTipText("Connect to Windows IIS FTP Service on 127.0.0.1:21 (anonymous)");
        presetIis.addActionListener(e -> applyPreset("127.0.0.1", "21", "anonymous", "guest@test.com", true));

        JButton presetAnon = new JButton("👤 Anonymous (:2121)");
        presetAnon.setToolTipText("Connect to SimpleFtpServer anonymously (read-only)");
        presetAnon.addActionListener(e -> applyPreset("127.0.0.1", "2121", "anonymous", "guest@test.com", true));

        presetRow.add(presetJava);
        presetRow.add(presetIis);
        presetRow.add(presetAnon);

        // Row 2: Connection Bar
        JPanel connRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        connRow.add(new JLabel("Host:"));
        connRow.add(hostField);
        connRow.add(new JLabel("Port:"));
        connRow.add(portField);
        connRow.add(new JLabel("User:"));
        connRow.add(userField);
        connRow.add(new JLabel("Pass:"));
        connRow.add(passField);
        connRow.add(new JLabel("Mode:"));
        connRow.add(modeCombo);
        connRow.add(connectBtn);

        statusDot.setForeground(COLOR_DISCONNECTED);
        statusDot.setFont(statusDot.getFont().deriveFont(Font.BOLD));
        connRow.add(Box.createHorizontalStrut(8));
        connRow.add(statusDot);

        topContainer.add(presetRow);
        topContainer.add(connRow);
        add(topContainer, BorderLayout.NORTH);

        // 2. Center Panel: Action Bar + Dual Explorer SplitPane
        JPanel centerContainer = new JPanel(new BorderLayout(4, 4));
        centerContainer.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        // Action Toolbar
        JPanel actionToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionToolbar.setBorder(BorderFactory.createTitledBorder("Actions"));
        uploadBtn.setFont(uploadBtn.getFont().deriveFont(Font.BOLD));
        downloadBtn.setFont(downloadBtn.getFont().deriveFont(Font.BOLD));
        actionToolbar.add(uploadBtn);
        actionToolbar.add(downloadBtn);
        actionToolbar.add(Box.createHorizontalStrut(10));
        actionToolbar.add(newFolderRemoteBtn);
        actionToolbar.add(deleteRemoteBtn);
        actionToolbar.add(Box.createHorizontalStrut(15));
        progressBar.setPreferredSize(new Dimension(150, 18));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        actionToolbar.add(progressBar);
        actionToolbar.add(progressLabel);

        centerContainer.add(actionToolbar, BorderLayout.NORTH);

        // Left Pane: Local Filesystem
        JPanel localPane = new JPanel(new BorderLayout(4, 4));
        localPane.setBorder(BorderFactory.createTitledBorder("Local Filesystem"));
        JPanel localHeader = new JPanel(new BorderLayout(4, 0));
        localHeader.add(localPathField, BorderLayout.CENTER);
        localPathField.setEditable(false);
        JPanel localBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        localBtns.add(localUpBtn);
        localBtns.add(localBrowseBtn);
        localBtns.add(localRefreshBtn);
        localHeader.add(localBtns, BorderLayout.EAST);
        localPane.add(localHeader, BorderLayout.NORTH);

        setupTableVisuals(localTable);
        localPane.add(new JScrollPane(localTable), BorderLayout.CENTER);

        // Right Pane: Remote Filesystem
        JPanel remotePane = new JPanel(new BorderLayout(4, 4));
        remotePane.setBorder(BorderFactory.createTitledBorder("Remote FTP Filesystem"));
        JPanel remoteHeader = new JPanel(new BorderLayout(4, 0));
        remoteHeader.add(remotePathField, BorderLayout.CENTER);
        remotePathField.setEditable(false);
        JPanel remoteBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        remoteBtns.add(remoteUpBtn);
        remoteBtns.add(remoteRefreshBtn);
        remoteHeader.add(remoteBtns, BorderLayout.EAST);
        remotePane.add(remoteHeader, BorderLayout.NORTH);

        setupTableVisuals(remoteTable);
        remotePane.add(new JScrollPane(remoteTable), BorderLayout.CENTER);

        // Split Pane (50/50)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPane, remotePane);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);
        centerContainer.add(splitPane, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Panel: RFC 959 Real-Time Communication Console
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 8, 6, 8),
                BorderFactory.createTitledBorder("RFC 959 Communication Console (Control Wire & Replies)")
        ));

        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setBackground(new Color(0x1E, 0x1E, 0x1E));
        consoleArea.setForeground(new Color(0xD4, 0xD4, 0xD4));
        consoleArea.setCaretColor(Color.WHITE);
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setPreferredSize(new Dimension(800, 160));
        bottomPanel.add(consoleScroll, BorderLayout.CENTER);

        JPanel consoleBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        consoleBar.add(clearLogBtn);
        bottomPanel.add(consoleBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupTableVisuals(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.setShowGrid(true);
        table.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String type = (String) tbl.getValueAt(row, 2);
                if ("Folder".equals(type) || "<DIR>".equals(tbl.getValueAt(row, 1))) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    if (!isSelected) {
                        c.setForeground(new Color(0x00, 0x55, 0x99));
                    }
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    if (!isSelected) {
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
    }

    private void initListeners() {
        // Observer listener on FTP Client
        ftpClient.addCommandListener(new FtpCommandListener() {
            @Override
            public void onCommandSent(String command) {
                appendConsole(">>> " + command, new Color(0x6A, 0x99, 0x55));
            }

            @Override
            public void onReplyReceived(int code, String replyText) {
                Color c = code >= 400 ? new Color(0xF4, 0x47, 0x47) : (code >= 200 && code < 300 ? new Color(0x4E, 0xC9, 0xB0) : new Color(0xCE, 0x91, 0x78));
                appendConsole("<<< " + replyText, c);
            }
        });

        // Clear console
        clearLogBtn.addActionListener(e -> consoleArea.setText(""));

        // Connect button
        connectBtn.addActionListener(e -> {
            if (ftpClient.isConnected()) {
                executeAsync("Disconnecting...", () -> {
                    ftpClient.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        updateConnectionState(false);
                        logInfo("Disconnected from FTP server.");
                    });
                });
            } else {
                doConnect();
            }
        });

        // Local Browse
        localBrowseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentLocalDir);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                setLocalDirectory(chooser.getSelectedFile());
            }
        });

        // Local Up
        localUpBtn.addActionListener(e -> {
            if (currentLocalDir != null && currentLocalDir.getParentFile() != null) {
                setLocalDirectory(currentLocalDir.getParentFile());
            }
        });

        // Local Refresh
        localRefreshBtn.addActionListener(e -> refreshLocalTable());

        // Local Table Double Click (Folder navigation)
        localTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && localTable.getSelectedRow() != -1) {
                    int row = localTable.getSelectedRow();
                    String name = (String) localTableModel.getValueAt(row, 0);
                    String type = (String) localTableModel.getValueAt(row, 2);
                    if ("Folder".equals(type) && currentLocalDir != null) {
                        File subDir = new File(currentLocalDir, name);
                        if (subDir.isDirectory()) {
                            setLocalDirectory(subDir);
                        }
                    }
                }
            }
        });

        // Remote Up
        remoteUpBtn.addActionListener(e -> {
            if (!ftpClient.isConnected()) return;
            executeAsync("Navigating up...", () -> {
                try {
                    ftpClient.changeToParentDirectory();
                    refreshRemoteTableAsync();
                } catch (IOException ex) {
                    showError("CDUP failed: " + ex.getMessage());
                }
            });
        });

        // Remote Refresh
        remoteRefreshBtn.addActionListener(e -> {
            if (!ftpClient.isConnected()) return;
            executeAsync("Refreshing remote listing...", this::refreshRemoteTableAsync);
        });

        // Remote Table Double Click (Folder navigation)
        remoteTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && remoteTable.getSelectedRow() != -1) {
                    int row = remoteTable.getSelectedRow();
                    String name = (String) remoteTableModel.getValueAt(row, 0);
                    String type = (String) remoteTableModel.getValueAt(row, 2);
                    if ("Folder".equals(type)) {
                        executeAsync("Entering remote folder: " + name, () -> {
                            try {
                                ftpClient.changeWorkingDirectory(name);
                                refreshRemoteTableAsync();
                            } catch (IOException ex) {
                                showError("Failed to open remote folder: " + ex.getMessage());
                            }
                        });
                    }
                }
            }
        });

        // Upload Button
        uploadBtn.addActionListener(e -> doUpload());

        // Download Button
        downloadBtn.addActionListener(e -> doDownload());

        // New Remote Folder Button
        newFolderRemoteBtn.addActionListener(e -> {
            if (!ftpClient.isConnected()) return;
            String folderName = JOptionPane.showInputDialog(this, "Enter new folder name:", "New Remote Folder", JOptionPane.PLAIN_MESSAGE);
            if (folderName != null && !folderName.isBlank()) {
                executeAsync("Creating folder: " + folderName, () -> {
                    try {
                        boolean ok = ftpClient.makeDirectory(folderName.trim());
                        if (ok) {
                            refreshRemoteTableAsync();
                            logInfo("Directory created: " + folderName);
                        } else {
                            showError("Server rejected MKD: " + (ftpClient.getLastReply() != null ? ftpClient.getLastReply().message() : "Error"));
                        }
                    } catch (IOException ex) {
                        showError("Failed to create folder: " + ex.getMessage());
                    }
                });
            }
        });

        // Delete Remote File/Folder
        deleteRemoteBtn.addActionListener(e -> {
            if (!ftpClient.isConnected()) return;
            int row = remoteTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Please select an item in the Remote Table to delete.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String name = (String) remoteTableModel.getValueAt(row, 0);
            String type = (String) remoteTableModel.getValueAt(row, 2);
            boolean isDir = "Folder".equals(type);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to permanently delete remote " + (isDir ? "directory" : "file") + ":\n\"" + name + "\"?",
                    "Confirm Remote Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                executeAsync("Deleting: " + name, () -> {
                    try {
                        boolean ok = isDir ? ftpClient.removeDirectory(name) : ftpClient.deleteFile(name);
                        if (ok) {
                            refreshRemoteTableAsync();
                            logInfo("Deleted remote item: " + name);
                        } else {
                            showError("Delete failed: " + (ftpClient.getLastReply() != null ? ftpClient.getLastReply().message() : "Error"));
                        }
                    } catch (IOException ex) {
                        showError("Failed to delete remote item: " + ex.getMessage());
                    }
                });
            }
        });

        // Window Close Listener (ensure clean disconnect)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (ftpClient.isConnected()) {
                    Thread.ofVirtual().start(ftpClient::disconnect);
                }
            }
        });
    }

    private void setInitialState() {
        setLocalDirectory(new File("."));
        updateConnectionState(false);
    }

    private void applyPreset(String host, String port, String user, String pass, boolean passive) {
        hostField.setText(host);
        portField.setText(port);
        userField.setText(user);
        passField.setText(pass);
        modeCombo.setSelectedIndex(passive ? 0 : 1);
        logInfo(String.format("Applied preset: %s:%s (User: %s)", host, port, user));
    }

    private void doConnect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be an integer (e.g. 2121 or 21).", "Invalid Port", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        boolean passive = modeCombo.getSelectedIndex() == 0;

        executeAsync("Connecting to " + host + ":" + port + "...", () -> {
            try {
                logInfo("Initiating TCP connection to " + host + ":" + port + "...");
                ftpClient.connect(host, port);
                ftpClient.setPassiveMode(passive);

                logInfo("Sending authentication credentials for: " + user);
                boolean loggedIn = ftpClient.login(user, pass);
                if (!loggedIn) {
                    ftpClient.disconnect();
                    showError("Login incorrect or rejected by server: " + (ftpClient.getLastReply() != null ? ftpClient.getLastReply().message() : "530"));
                    SwingUtilities.invokeLater(() -> updateConnectionState(false));
                    return;
                }

                logInfo("Successfully logged in as: " + user);
                SwingUtilities.invokeLater(() -> {
                    updateConnectionState(true);
                    statusDot.setText("● Connected to " + host + ":" + port + " (" + user + ")");
                });
                refreshRemoteTableAsync();

            } catch (Exception ex) {
                ftpClient.disconnect();
                showError("Connection failed: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> updateConnectionState(false));
            }
        });
    }

    private void doUpload() {
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "You must be connected to an FTP server first.", "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int row = localTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a local file to upload.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = (String) localTableModel.getValueAt(row, 0);
        String type = (String) localTableModel.getValueAt(row, 2);
        if ("Folder".equals(type)) {
            JOptionPane.showMessageDialog(this, "Folder upload not directly supported. Please create a folder and upload files inside.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File localFile = new File(currentLocalDir, name);
        if (!localFile.exists() || !localFile.isFile()) {
            showError("Local file does not exist: " + localFile.getAbsolutePath());
            return;
        }

        executeAsync("Uploading " + name + " (" + formatFileSize(localFile.length()) + ")...", () -> {
            try {
                long start = System.currentTimeMillis();
                ftpClient.uploadFile(localFile, name);
                long elapsed = Math.max(1, System.currentTimeMillis() - start);
                logInfo(String.format("Upload finished: %s (%d bytes in %d ms)", name, localFile.length(), elapsed));
                refreshRemoteTableAsync();
            } catch (Exception ex) {
                showError("Upload failed: " + ex.getMessage());
            }
        });
    }

    private void doDownload() {
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "You must be connected to an FTP server first.", "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a remote file to download.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = (String) remoteTableModel.getValueAt(row, 0);
        String type = (String) remoteTableModel.getValueAt(row, 2);
        if ("Folder".equals(type)) {
            JOptionPane.showMessageDialog(this, "Direct directory download is not supported. Navigate inside and download individual files.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File targetLocalFile = new File(currentLocalDir, name);
        if (targetLocalFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "Local file \"" + name + "\" already exists. Overwrite?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        executeAsync("Downloading " + name + "...", () -> {
            try {
                long start = System.currentTimeMillis();
                ftpClient.downloadFile(name, targetLocalFile);
                long elapsed = Math.max(1, System.currentTimeMillis() - start);
                logInfo(String.format("Download finished: %s (%d bytes in %d ms)", name, targetLocalFile.length(), elapsed));
                SwingUtilities.invokeLater(this::refreshLocalTable);
            } catch (Exception ex) {
                showError("Download failed: " + ex.getMessage());
            }
        });
    }

    private void refreshRemoteTableAsync() {
        try {
            String curDir = ftpClient.printWorkingDirectory();
            List<FtpFile> files = ftpClient.listFiles();
            SwingUtilities.invokeLater(() -> {
                remotePathField.setText(curDir);
                currentRemoteFiles = files;
                remoteTableModel.setRowCount(0);
                for (FtpFile f : files) {
                    remoteTableModel.addRow(new Object[]{
                            f.getName(),
                            f.getFormattedSize(),
                            f.getType(),
                            f.getLastModified()
                    });
                }
            });
        } catch (IOException ex) {
            showError("Failed to fetch remote file listing: " + ex.getMessage());
        }
    }

    private void setLocalDirectory(File dir) {
        try {
            currentLocalDir = dir.getCanonicalFile();
        } catch (IOException e) {
            currentLocalDir = dir.getAbsoluteFile();
        }
        localPathField.setText(currentLocalDir.getAbsolutePath());
        refreshLocalTable();
    }

    private void refreshLocalTable() {
        localTableModel.setRowCount(0);
        if (currentLocalDir == null || !currentLocalDir.exists() || !currentLocalDir.isDirectory()) {
            return;
        }
        File[] files = currentLocalDir.listFiles();
        if (files == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (File f : files) {
            String name = f.getName();
            boolean isDir = f.isDirectory();
            String size = isDir ? "<DIR>" : formatFileSize(f.length());
            String type = isDir ? "Folder" : getFileExtensionType(name);
            String date = sdf.format(new Date(f.lastModified()));
            localTableModel.addRow(new Object[]{name, size, type, date});
        }
    }

    private void updateConnectionState(boolean isConn) {
        connectBtn.setText(isConn ? "Disconnect" : "Connect");
        statusDot.setText(isConn ? "● Connected" : "● Disconnected");
        statusDot.setForeground(isConn ? COLOR_CONNECTED : COLOR_DISCONNECTED);

        hostField.setEnabled(!isConn);
        portField.setEnabled(!isConn);
        userField.setEnabled(!isConn);
        passField.setEnabled(!isConn);
        modeCombo.setEnabled(!isConn);

        remoteUpBtn.setEnabled(isConn);
        remoteRefreshBtn.setEnabled(isConn);
        uploadBtn.setEnabled(isConn);
        downloadBtn.setEnabled(isConn);
        newFolderRemoteBtn.setEnabled(isConn);
        deleteRemoteBtn.setEnabled(isConn);

        if (!isConn) {
            remoteTableModel.setRowCount(0);
            remotePathField.setText("/");
        }
    }

    private void executeAsync(String statusMessage, Runnable task) {
        setBusy(true, statusMessage);
        Thread.ofVirtual().start(() -> {
            try {
                task.run();
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false, "Ready."));
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        progressLabel.setText(message);
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);

        connectBtn.setEnabled(!busy);
        uploadBtn.setEnabled(!busy && ftpClient.isConnected());
        downloadBtn.setEnabled(!busy && ftpClient.isConnected());
        newFolderRemoteBtn.setEnabled(!busy && ftpClient.isConnected());
        deleteRemoteBtn.setEnabled(!busy && ftpClient.isConnected());
        remoteRefreshBtn.setEnabled(!busy && ftpClient.isConnected());
    }

    private void appendConsole(String line, Color color) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(line + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    private void logInfo(String message) {
        appendConsole("[INFO] " + message, Color.LIGHT_GRAY);
    }

    private void showError(String message) {
        logInfo("[ERROR] " + message);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, message, "FTP Operation Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(java.util.Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }

    private static String getFileExtensionType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot != -1 && dot < filename.length() - 1) {
            return filename.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) + " File";
        }
        return "File";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FtpClientApp app = new FtpClientApp();
            app.setVisible(true);
        });
    }
}
