/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.ui;

import ai.emailclaw.emailclaw.storage.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Debug log view.
 *
 * <p>Used to view application logs, filter levels, and auto-refresh.
 */
public class DebugView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(DebugView.class.getName());
    private final AppPaths paths;
    private final VBox root = new VBox(12);
    private final TextArea logArea = new TextArea();
    private final ComboBox<String> levelFilter = new ComboBox<>();
    private final CheckBox autoRefresh = new CheckBox("Auto Refresh");
    private final Label statusLabel = new Label();
    private Timeline refreshTimer;

    public DebugView(AppPaths paths) {
        this.paths = paths;
        initUi();
        loadLogs();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        Label title = new Label("Debug");
        title.getStyleClass().add("page-title");
        Label desc = new Label("View application logs and diagnostic information.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        // Controls bar
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        levelFilter.getItems().addAll("ALL", "DEBUG", "INFO", "WARN", "ERROR");
        levelFilter.setValue("ALL");
        levelFilter.setOnAction(e -> loadLogs());
        autoRefresh.setSelected(false);
        autoRefresh.setOnAction(e -> toggleAutoRefresh());
        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("chip-btn");
        refreshBtn.setOnAction(e -> loadLogs());
        Button clearBtn = new Button("Clear Display");
        clearBtn.getStyleClass().add("chip-btn");
        clearBtn.setOnAction(e -> logArea.clear());
        statusLabel.getStyleClass().add("muted");
        controls.getChildren()
                .addAll(
                        new Label("Level:"),
                        levelFilter,
                        autoRefresh,
                        refreshBtn,
                        clearBtn,
                        statusLabel);

        // System info
        VBox sysInfo = new VBox(4);
        sysInfo.getStyleClass().add("card");
        Label sysTitle = new Label("System Information");
        sysTitle.getStyleClass().add("fw-600");
        sysInfo.getChildren()
                .addAll(
                        sysTitle,
                        infoRow(
                                "Java",
                                System.getProperty("java.version")
                                        + " ("
                                        + System.getProperty("java.vendor")
                                        + ")"),
                        infoRow(
                                "OS",
                                System.getProperty("os.name")
                                        + " "
                                        + System.getProperty("os.arch")),
                        infoRow("JavaFX", System.getProperty("javafx.version", "N/A")),
                        infoRow("Working Dir", paths.root.toString()),
                        infoRow(
                                "Memory",
                                Runtime.getRuntime().freeMemory() / 1024 / 1024
                                        + " MB free / "
                                        + Runtime.getRuntime().maxMemory() / 1024 / 1024
                                        + " MB max"));
        for (String line : windowsDiagnostics()) {
            sysInfo.getChildren().add(infoRow("Windows", line));
        }

        // Log viewer
        logArea.setEditable(false);
        logArea.getStyleClass().add("editor-dark");
        logArea.setPrefHeight(500);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        root.getChildren().addAll(title, desc, sysInfo, controls, logArea);
    }

    private HBox infoRow(String key, String value) {
        HBox h = new HBox(8);
        Label k = new Label(key + ":");
        k.getStyleClass().add("fw-600");
        k.setPrefWidth(100);
        Label v = new Label(value);
        v.getStyleClass().add("muted");
        h.getChildren().addAll(k, v);
        return h;
    }

    private void loadLogs() {
        LOGGER.fine("Read log files and refresh debug panel");
        try {
            Path logDir = paths.logsDir;
            if (!Files.exists(logDir)) {
                logArea.setText(
                        "["
                                + now()
                                + "] Log directory not found: "
                                + logDir
                                + "\n"
                                + "["
                                + now()
                                + "] Application logs will appear here once generated.\n");
                statusLabel.setText("No log files");
                return;
            }
            List<Path> logFiles;
            try (Stream<Path> s = Files.list(logDir)) {
                logFiles = s.filter(p -> p.toString().endsWith(".log")).sorted().toList();
            }
            if (logFiles.isEmpty()) {
                logArea.setText(
                        "["
                                + now()
                                + "] No log files found.\n"
                                + "["
                                + now()
                                + "] INFO  Application started successfully.\n"
                                + "["
                                + now()
                                + "] INFO  Emailclaw v1.0-SNAPSHOT ready.\n");
                statusLabel.setText("0 log files");
                return;
            }
            Path latest = logFiles.getLast();
            List<String> lines = Files.readAllLines(latest);
            String filter = levelFilter.getValue();
            if (!"ALL".equals(filter)) {
                lines = lines.stream().filter(l -> l.contains(filter)).toList();
            }
            int tail = Math.min(lines.size(), 500);
            logArea.setText(String.join("\n", lines.subList(lines.size() - tail, lines.size())));
            logArea.positionCaret(logArea.getText().length());
            statusLabel.setText(logFiles.size() + " log file(s), showing last " + tail + " lines");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to read logs", ex);
            logArea.setText("[ERROR] Failed to read logs: " + ex.getMessage());
            statusLabel.setText("Error");
        }
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private void toggleAutoRefresh() {
        if (autoRefresh.isSelected()) {
            refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> loadLogs()));
            refreshTimer.setCycleCount(Timeline.INDEFINITE);
            refreshTimer.play();
        } else if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        loadLogs();
    }

    private List<String> windowsDiagnostics() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("windows")) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int cwdLen = paths.root.toAbsolutePath().toString().length();
        lines.add("Working path length: " + cwdLen + (cwdLen >= 220 ? " (close to MAX_PATH)" : ""));
        lines.add("Long paths: " + detectWindowsLongPathStatus());
        lines.add("PowerShell language mode: " + detectPowerShellLanguageMode());
        return lines;
    }

    private String detectWindowsLongPathStatus() {
        try {
            Process p =
                    new ProcessBuilder(
                                    "reg",
                                    "query",
                                    "HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem",
                                    "/v",
                                    "LongPathsEnabled")
                            .start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (out.contains("0x1")) {
                return "enabled";
            }
            if (out.contains("0x0")) {
                return "disabled";
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown (" + e.getMessage() + ")";
        }
    }

    private String detectPowerShellLanguageMode() {
        for (String executable : List.of("powershell.exe", "powershell", "pwsh.exe", "pwsh")) {
            try {
                Process p =
                        new ProcessBuilder(
                                        executable,
                                        "-NoLogo",
                                        "-NoProfile",
                                        "-NonInteractive",
                                        "-Command",
                                        "$ExecutionContext.SessionState.LanguageMode")
                                .start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                if (!out.isBlank()) {
                    return out.lines().reduce((a, b) -> b).orElse(out);
                }
            } catch (Exception ignored) {
            }
        }
        return "unknown";
    }
}
