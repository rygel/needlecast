package io.github.sparkle4j.ui;

import io.github.sparkle4j.Sparkle4jConfig;
import io.github.sparkle4j.UpdateDownloader;
import io.github.sparkle4j.UpdateItem;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Modal Swing dialog that drives the update flow:
 * <ol>
 *   <li>Announcement state: release notes + Skip / Remind Me Later / Install buttons</li>
 *   <li>Download state: progress bar + Cancel</li>
 *   <li>Ready state: confirmation message + Install Now</li>
 * </ol>
 */
public final class UpdateDialog {

    private static final Logger log = Logger.getLogger(UpdateDialog.class.getName());

    private final Sparkle4jConfig config;
    private final UpdateItem item;
    private final Consumer<String> onSkip;
    private final BiConsumer<UpdateItem, Path> onInstall;

    private JDialog dialog;
    private JPanel buttonPanel;
    private JPanel progressPanel;
    private JProgressBar progressBar;
    private JLabel progressLabel;

    private volatile Thread downloadThread;
    private volatile boolean cancelled;

    public UpdateDialog(Sparkle4jConfig config, UpdateItem item,
                        Consumer<String> onSkip, BiConsumer<UpdateItem, Path> onInstall) {
        this.config = config;
        this.item = item;
        this.onSkip = onSkip;
        this.onInstall = onInstall;
    }

    public void show() {
        var parentWindow = config.parentComponent() != null
            ? SwingUtilities.getWindowAncestor(config.parentComponent())
            : null;

        dialog = new JDialog(parentWindow, "Update Available", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 0));

        dialog.add(createHeaderPanel(), BorderLayout.NORTH);
        dialog.add(createNotesScrollPane(), BorderLayout.CENTER);

        buttonPanel = createButtonPanel();
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        progressPanel = createProgressPanel();

        dialog.pack();
        dialog.setLocationRelativeTo(config.parentComponent());
        dialog.setVisible(true);
    }

    private JPanel createHeaderPanel() {
        var panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new EmptyBorder(16, 16, 8, 16));

        var titleLabel = new JLabel("<html><b>" + config.appName() + " " + item.shortVersionString() + " is available</b></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 2f));
        panel.add(titleLabel, BorderLayout.NORTH);

        panel.add(new JLabel("<html>You have " + config.currentVersion() + ". Would you like to update?</html>"), BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createNotesScrollPane() {
        var notesPanel = new ReleaseNotesPanel(item.releaseNotesUrl(), item.inlineDescription());
        var scrollPane = new JScrollPane(notesPanel);
        scrollPane.setPreferredSize(new Dimension(600, 280));
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(0, 16, 0, 16),
            BorderFactory.createEtchedBorder()
        ));
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        var skipButton = new JButton("Skip This Version");
        skipButton.setVisible(!item.isCriticalUpdate());
        var remindButton = new JButton("Remind Me Later");
        var installButton = new JButton("Install");
        dialog.getRootPane().setDefaultButton(installButton);

        skipButton.addActionListener(e -> { onSkip.accept(item.version()); dialog.dispose(); });
        remindButton.addActionListener(e -> dialog.dispose());
        installButton.addActionListener(e -> startDownload());

        var panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setBorder(new EmptyBorder(8, 16, 16, 16));
        panel.add(skipButton);
        panel.add(remindButton);
        panel.add(installButton);
        return panel;
    }

    private JPanel createProgressPanel() {
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressLabel = new JLabel("Downloading\u2026");
        var cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancelDownload());

        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 16, 16, 16));
        panel.add(progressLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(cancelButton, BorderLayout.EAST);
        return panel;
    }

    private void startDownload() {
        dialog.remove(buttonPanel);
        dialog.add(progressPanel, BorderLayout.SOUTH);
        dialog.revalidate();
        dialog.repaint();
        cancelled = false;

        downloadThread = new Thread(() -> {
            try {
                var tempFile = new UpdateDownloader().download(item.url(), item.length(), (received, total) -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException(new InterruptedException("Cancelled"));
                    }
                    SwingUtilities.invokeLater(() -> {
                        int pct = total > 0 ? (int) (received * 100 / total) : 0;
                        progressBar.setValue(pct);
                        progressLabel.setText("Downloading\u2026 " + pct + "%");
                    });
                });
                if (!cancelled) {
                    SwingUtilities.invokeLater(() -> switchToReady(tempFile));
                } else {
                    tempFile.toFile().delete();
                }
            } catch (InterruptedException e) {
                // User cancelled
            } catch (IOException e) {
                if (!cancelled) {
                    log.warning("Download failed: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog, "Download failed. Try again later.",
                            "Download Error", JOptionPane.ERROR_MESSAGE);
                        dialog.dispose();
                    });
                }
            } catch (RuntimeException e) {
                if (e.getCause() instanceof InterruptedException) {
                    // User cancelled via progress callback
                } else {
                    throw e;
                }
            }
        });
        downloadThread.setDaemon(true);
        downloadThread.setName("sparkle4j-download");
        downloadThread.start();
    }

    private void switchToReady(Path tempFile) {
        progressPanel.removeAll();
        progressPanel.setLayout(new BorderLayout(8, 0));
        progressPanel.add(
            new JLabel("<html>Ready to install. The app will quit and the update will be applied.</html>"),
            BorderLayout.CENTER
        );
        var installNowButton = new JButton("Install Now");
        installNowButton.addActionListener(e -> {
            dialog.dispose();
            var t = new Thread(() -> onInstall.accept(item, tempFile));
            t.setDaemon(false);
            t.setName("sparkle4j-install");
            t.start();
        });
        progressPanel.add(installNowButton, BorderLayout.EAST);
        dialog.revalidate();
        dialog.repaint();
    }

    private void cancelDownload() {
        cancelled = true;
        if (downloadThread != null) downloadThread.interrupt();
        try {
            var lastSlash = item.url().lastIndexOf('/');
            var raw = lastSlash >= 0 ? item.url().substring(lastSlash + 1) : item.url();
            var qmark = raw.indexOf('?');
            var fileName = qmark >= 0 ? raw.substring(0, qmark) : raw;
            if (fileName.isBlank()) fileName = "sparkle4j-update";
            var tempFile = Path.of(System.getProperty("java.io.tmpdir"), "sparkle4j-" + fileName);
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // best effort cleanup
        }
        dialog.dispose();
    }
}
