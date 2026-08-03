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
package ai.emailclaw.emailclaw.channel.dingtalk.ui;

import ai.emailclaw.emailclaw.channel.dingtalk.DingTalkChannelConfig;
import ai.emailclaw.emailclaw.channel.dingtalk.DingTalkConfigKeys;
import ai.emailclaw.emailclaw.channel.dingtalk.DingTalkPlugin;
import ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DingTalk configuration view provider.
 */
public class DingTalkConfigViewProvider implements CustomConfigViewProvider {

    private static final Logger LOGGER =
            Logger.getLogger(DingTalkConfigViewProvider.class.getName());
    private static final String DINGTALK_API_BASE = "https://oapi.dingtalk.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String targetPluginId() {
        return DingTalkPlugin.ID_DINGTALK;
    }

    @Override
    public Node buildView(
            Map<String, Object> initialConfig,
            Consumer<Map<String, Object>> onSave,
            Runnable onCancel) {
        return new DingTalkConfigPane(initialConfig, onSave, onCancel);
    }

    private static class DingTalkConfigPane extends VBox {
        private final Map<String, Object> config;
        private final Consumer<Map<String, Object>> onSave;
        private final Runnable onCancel;

        private final ToggleButton enabledToggle = new ToggleButton();
        private final TextField botPrefixField = new TextField();
        private final ToggleButton showToolToggle = new ToggleButton();
        private final ToggleButton showThinkingToggle = new ToggleButton();
        private final TextField clientIdField = new TextField();
        private final PasswordField clientSecretField = new PasswordField();
        private final Button showSecretBtn = new Button("👁");
        private final ComboBox<String> messageTypeCombo = new ComboBox<>();
        private final ComboBox<String> cronMessageTypeCombo = new ComboBox<>();
        private final ToggleButton atSenderToggle = new ToggleButton();
        private final ComboBox<String> dmPolicyCombo = new ComboBox<>();
        private final ComboBox<String> groupPolicyCombo = new ComboBox<>();
        private final ToggleButton requireMentionToggle = new ToggleButton();
        private final TextField allowlistField = new TextField();
        private final Label allowlistTagsLabel = new Label();
        private final Button getQrCodeBtn = new Button("Get DingTalk QR Code");
        private final VBox qrCodeBox = new VBox(8);
        private final ImageView qrCodeView = new ImageView();
        private final Label qrStatusLabel = new Label();
        private final AtomicBoolean polling = new AtomicBoolean(false);

        public DingTalkConfigPane(
                Map<String, Object> initialConfig,
                Consumer<Map<String, Object>> onSave,
                Runnable onCancel) {
            this.config = new java.util.HashMap<>(initialConfig);
            this.onSave = onSave;
            this.onCancel = onCancel;
            this.setPadding(new Insets(20));

            // To emulate channel saving, we use an in-memory proxy channel object
            ai.emailclaw.emailclaw.model.ChannelInfo proxyChannel =
                    new ai.emailclaw.emailclaw.model.ChannelInfo(
                            DingTalkPlugin.ID_DINGTALK, "DingTalk", false, false);
            proxyChannel.setPluginConfig(this.config);
            // Copy top-level flags
            proxyChannel.setEnabled((Boolean) this.config.getOrDefault("enabled", false));
            proxyChannel.setBotPrefix((String) this.config.getOrDefault("botPrefix", ""));

            buildContent(proxyChannel);
            loadValues(proxyChannel);

            // Hook into scene to catch window close for polling
            this.sceneProperty()
                    .addListener(
                            (obs, oldScene, newScene) -> {
                                if (newScene != null) {
                                    newScene.windowProperty()
                                            .addListener(
                                                    (wObs, oldWindow, newWindow) -> {
                                                        if (newWindow != null) {
                                                            newWindow.setOnCloseRequest(
                                                                    e -> polling.set(false));
                                                        }
                                                    });
                                }
                            });

            // Add a manual "Save" button since we are returning a Node
            // The Dialog wrapper in ChannelsView would usually have the button,
            // but for safety we can trigger onSave when values change or via a button
            Button manualSaveBtn = new Button("Save Configuration");
            manualSaveBtn.getStyleClass().add("btn-primary");
            manualSaveBtn.setOnAction(
                    e -> {
                        saveValues(proxyChannel);
                        // merge top-level back
                        this.config.put("enabled", proxyChannel.isEnabled());
                        this.config.put("botPrefix", proxyChannel.getBotPrefix());
                        onSave.accept(this.config);
                    });

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setOnAction(
                    e -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    });

            HBox btnBox = new HBox(8, cancelBtn, manualSaveBtn);
            btnBox.setAlignment(Pos.BOTTOM_RIGHT);
            btnBox.setPadding(new Insets(20, 0, 0, 0));
            this.getChildren().add(btnBox);
        }

        private void buildContent(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("pane-transparent");
            VBox form = new VBox(14);
            form.setPadding(new Insets(20, 24, 20, 24));
            HBox titleRow = new HBox(10);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Label titleLabel = new Label("DingTalk Settings");
            titleLabel.getStyleClass().addAll("text-18", "fw-700");
            Hyperlink docLink = new Hyperlink("DingTalk Doc");
            docLink.getStyleClass().add("text-orange");
            docLink.setOnAction(
                    e -> {
                        try {
                            Desktop.getDesktop()
                                    .browse(
                                            URI.create(
                                                    "https://open.dingtalk.com/document/orgapp/overview-of-development-process"));
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Unable to open link", ex);
                        }
                    });
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            titleRow.getChildren().addAll(titleLabel, spacer, docLink);
            form.getChildren()
                    .addAll(
                            titleRow,
                            fieldRow("Enabled", styledToggle(enabledToggle)),
                            fieldRow("Bot Prefix", botPrefixField),
                            fieldRowWithInfo("Show Tool Messages", styledToggle(showToolToggle)),
                            fieldRowWithInfo("Show Thinking", styledToggle(showThinkingToggle)));
            VBox infoCard = new VBox(4);
            infoCard.getStyleClass().add("badge-info");
            HBox infoRow = new HBox(8);
            infoRow.setAlignment(Pos.TOP_LEFT);
            Label infoIcon = new Label("ℹ");
            infoIcon.getStyleClass().add("text-blue-14");
            TextFlow infoText =
                    new TextFlow(
                            new Text("Scan the QR code with DingTalk to create a bot instantly.\n"),
                            new Text("A DingTalk app will be created automatically and Client\n"),
                            new Text("ID / Client Secret will be filled in."));
            infoText.getStyleClass().add("text-13");
            infoRow.getChildren().addAll(infoIcon, infoText);
            infoCard.getChildren().add(infoRow);
            Label scanLabel = new Label("Scan to Create Bot");
            scanLabel.getStyleClass().add("fw-600");
            getQrCodeBtn.setMaxWidth(Double.MAX_VALUE);
            getQrCodeBtn.getStyleClass().add("btn-orange-lg");
            getQrCodeBtn.setOnAction(e -> fetchQrCode());
            qrCodeBox.setAlignment(Pos.CENTER);
            qrCodeBox.setVisible(false);
            qrCodeBox.setManaged(false);
            qrCodeView.setFitWidth(200);
            qrCodeView.setFitHeight(200);
            qrCodeView.setPreserveRatio(true);
            qrStatusLabel.getStyleClass().add("text-13-gray");
            qrCodeBox.getChildren().addAll(qrCodeView, qrStatusLabel);
            clientIdField.setPromptText("dingxxxxx");
            clientIdField.getStyleClass().add("input-bordered");
            clientSecretField.getStyleClass().add("input-bordered");
            HBox secretRow = new HBox(6);
            secretRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(clientSecretField, Priority.ALWAYS);
            showSecretBtn.getStyleClass().add("btn-ghost");
            showSecretBtn.setOnAction(e -> toggleSecretVisibility());
            secretRow.getChildren().addAll(clientSecretField, showSecretBtn);
            messageTypeCombo.getItems().addAll("markdown", "text", "sampleMarkdown");
            cronMessageTypeCombo.getItems().addAll("markdown", "text", "sampleMarkdown");
            dmPolicyCombo.getItems().addAll("Open", "Closed");
            groupPolicyCombo.getItems().addAll("Open", "Closed");
            allowlistField.setPromptText("Enter user ID and press Enter to add");
            allowlistField.setOnAction(
                    e -> addAllowlistUser(channel, allowlistField.getText().trim()));
            HBox allowlistRow = new HBox(6, allowlistField);
            allowlistRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(allowlistField, Priority.ALWAYS);
            form.getChildren()
                    .addAll(
                            infoCard,
                            scanLabel,
                            getQrCodeBtn,
                            qrCodeBox,
                            fieldRowRequired("Client ID", clientIdField),
                            fieldRowRequired("Client Secret", secretRow),
                            fieldRowWithInfo("Message Type", messageTypeCombo),
                            fieldRowWithInfo("Cron Message Type", cronMessageTypeCombo),
                            fieldRowWithInfo("@ Sender on Reply", styledToggle(atSenderToggle)),
                            fieldRowWithInfo("DM Policy", dmPolicyCombo),
                            fieldRowWithInfo("Group Policy", groupPolicyCombo),
                            fieldRowWithInfo(
                                    "Require @Mention", styledToggle(requireMentionToggle)),
                            fieldRowWithInfo("Allowlist Users", allowlistRow),
                            allowlistTagsLabel);
            scroll.setContent(form);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            this.getChildren().add(scroll);
        }

        private Node styledToggle(ToggleButton toggle) {
            toggle.selectedProperty().addListener((obs, o, n) -> updateToggleStyle(toggle));
            updateToggleStyle(toggle);
            return toggle;
        }

        private void updateToggleStyle(ToggleButton toggle) {
            if (toggle.isSelected()) {
                toggle.setText("ON");
                toggle.setStyle(
                        "-fx-background-color: #ff8800; -fx-text-fill: white; -fx-font-weight: 700;"
                            + " -fx-background-radius: 20; -fx-padding: 4 14; -fx-cursor: hand;");
            } else {
                toggle.setText("OFF");
                toggle.setStyle(
                        "-fx-background-color: #cccccc; -fx-text-fill: white; -fx-font-weight: 600;"
                            + " -fx-background-radius: 20; -fx-padding: 4 14; -fx-cursor: hand;");
            }
        }

        private Node fieldRow(String label, Node control) {
            VBox box = new VBox(6);
            Label lbl = new Label(label);
            lbl.getStyleClass().add("fw-600");
            box.getChildren().addAll(lbl, control);
            return box;
        }

        private Node fieldRowRequired(String label, Node control) {
            VBox box = new VBox(6);
            HBox labelRow = new HBox(4);
            Label lbl = new Label(label);
            lbl.getStyleClass().add("fw-600");
            Label req = new Label("*");
            req.getStyleClass().add("text-red-700");
            labelRow.getChildren().addAll(lbl, req);
            box.getChildren().addAll(labelRow, control);
            return box;
        }

        private Node fieldRowWithInfo(String label, Node control) {
            VBox box = new VBox(6);
            Label lbl = new Label(label);
            lbl.getStyleClass().add("fw-600");
            box.getChildren().addAll(lbl, control);
            if (control instanceof ComboBox<?> combo) {
                combo.setMaxWidth(Double.MAX_VALUE);
            }
            return box;
        }

        private void addAllowlistUser(
                ai.emailclaw.emailclaw.model.ChannelInfo channel, String userId) {
            if (userId.isEmpty()) return;
            DingTalkChannelConfig.addAllowlistUser(channel, userId);
            allowlistField.clear();
            refreshAllowlistTags(channel);
        }

        private void refreshAllowlistTags(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            var users = DingTalkChannelConfig.getAllowlistUsers(channel);
            allowlistTagsLabel.setText(users.isEmpty() ? "" : "Users: " + String.join(", ", users));
            allowlistTagsLabel.getStyleClass().addAll("text-12", "text-gray");
        }

        private void loadValues(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            enabledToggle.setSelected(channel.isEnabled());
            updateToggleStyle(enabledToggle);
            botPrefixField.setText(channel.getBotPrefix() == null ? "" : channel.getBotPrefix());
            showToolToggle.setSelected(DingTalkChannelConfig.isShowToolMessages(channel));
            updateToggleStyle(showToolToggle);
            showThinkingToggle.setSelected(DingTalkChannelConfig.isShowThinking(channel));
            updateToggleStyle(showThinkingToggle);
            clientIdField.setText(DingTalkChannelConfig.getClientId(channel));
            clientSecretField.setText(DingTalkChannelConfig.getClientSecret(channel));
            messageTypeCombo.setValue(DingTalkChannelConfig.getMessageType(channel));
            cronMessageTypeCombo.setValue(DingTalkChannelConfig.getCronMessageType(channel));
            atSenderToggle.setSelected(DingTalkChannelConfig.isAtSenderOnReply(channel));
            updateToggleStyle(atSenderToggle);
            dmPolicyCombo.setValue(capitalize(DingTalkChannelConfig.getDmPolicy(channel)));
            groupPolicyCombo.setValue(capitalize(DingTalkChannelConfig.getGroupPolicy(channel)));
            requireMentionToggle.setSelected(DingTalkChannelConfig.isRequireMention(channel));
            updateToggleStyle(requireMentionToggle);
            refreshAllowlistTags(channel);
        }

        private void saveValues(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            channel.setEnabled(enabledToggle.isSelected());
            channel.setBotPrefix(botPrefixField.getText().trim());
            DingTalkChannelConfig.setShowToolMessages(channel, showToolToggle.isSelected());
            DingTalkChannelConfig.setShowThinking(channel, showThinkingToggle.isSelected());
            DingTalkChannelConfig.setClientId(channel, clientIdField.getText().trim());
            DingTalkChannelConfig.setClientSecret(channel, clientSecretField.getText().trim());
            DingTalkChannelConfig.setMessageType(
                    channel,
                    valueOrDefault(
                            messageTypeCombo.getValue(), DingTalkConfigKeys.DEFAULT_MESSAGE_TYPE));
            DingTalkChannelConfig.setCronMessageType(
                    channel,
                    valueOrDefault(
                            cronMessageTypeCombo.getValue(),
                            DingTalkConfigKeys.DEFAULT_MESSAGE_TYPE));
            DingTalkChannelConfig.setAtSenderOnReply(channel, atSenderToggle.isSelected());
            DingTalkChannelConfig.setDmPolicy(
                    channel,
                    valueOrDefault(dmPolicyCombo.getValue(), DingTalkConfigKeys.POLICY_OPEN)
                            .toLowerCase());
            DingTalkChannelConfig.setGroupPolicy(
                    channel,
                    valueOrDefault(groupPolicyCombo.getValue(), DingTalkConfigKeys.POLICY_OPEN)
                            .toLowerCase());
            DingTalkChannelConfig.setRequireMention(channel, requireMentionToggle.isSelected());
        }

        private void toggleSecretVisibility() {
            String current = clientSecretField.getText();
            Tooltip tip = new Tooltip(current.isEmpty() ? "(empty)" : current);
            tip.setAutoHide(true);
            Tooltip.install(showSecretBtn, tip);
            if (showSecretBtn.getScene() != null) {
                tip.show(showSecretBtn.getScene().getWindow());
            }
        }

        private String capitalize(String s) {
            if (s == null || s.isEmpty()) return "Open";
            return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
        }

        private String valueOrDefault(String val, String def) {
            return (val == null || val.isBlank()) ? def : val;
        }

        private void fetchQrCode() {
            getQrCodeBtn.setDisable(true);
            getQrCodeBtn.setText("Getting QR Code...");
            qrStatusLabel.setText("Connecting to DingTalk...");
            qrStatusLabel.getStyleClass().add("text-13-gray");
            qrCodeBox.setVisible(true);
            qrCodeBox.setManaged(true);
            qrCodeView.setImage(null);
            polling.set(false);
            Thread.startVirtualThread(
                    () -> {
                        try {
                            HttpClient http =
                                    HttpClient.newBuilder()
                                            .connectTimeout(Duration.ofSeconds(15))
                                            .build();
                            String initBody = "{\"source\":\"EMAILCLAW\"}";
                            HttpRequest initReq =
                                    HttpRequest.newBuilder()
                                            .uri(
                                                    URI.create(
                                                            DINGTALK_API_BASE
                                                                    + "/app/registration/init"))
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofString(initBody))
                                            .timeout(Duration.ofSeconds(15))
                                            .build();
                            HttpResponse<String> initResp =
                                    http.send(initReq, HttpResponse.BodyHandlers.ofString());
                            JsonNode initData = JSON.readTree(initResp.body());
                            int errcode = initData.path("errcode").asInt(-1);
                            if (errcode != 0) {
                                String msg = initData.path("errmsg").asString("unknown error");
                                Platform.runLater(
                                        () -> showFetchError("DingTalk init failed: " + msg));
                                return;
                            }
                            String nonce = initData.path("nonce").asString("");
                            if (nonce.isEmpty()) {
                                Platform.runLater(
                                        () -> showFetchError("DingTalk returned empty nonce"));
                                return;
                            }
                            String beginBody = "{\"nonce\":\"" + nonce + "\"}";
                            HttpRequest beginReq =
                                    HttpRequest.newBuilder()
                                            .uri(
                                                    URI.create(
                                                            DINGTALK_API_BASE
                                                                    + "/app/registration/begin"))
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofString(beginBody))
                                            .timeout(Duration.ofSeconds(15))
                                            .build();
                            HttpResponse<String> beginResp =
                                    http.send(beginReq, HttpResponse.BodyHandlers.ofString());
                            JsonNode beginData = JSON.readTree(beginResp.body());
                            int beginErr = beginData.path("errcode").asInt(-1);
                            if (beginErr != 0) {
                                String msg = beginData.path("errmsg").asString("unknown error");
                                Platform.runLater(
                                        () -> showFetchError("DingTalk begin failed: " + msg));
                                return;
                            }
                            String deviceCode = beginData.path("device_code").asString("");
                            String scanUrl =
                                    beginData.path("verification_uri_complete").asString("");
                            if (deviceCode.isEmpty() || scanUrl.isEmpty()) {
                                Platform.runLater(
                                        () ->
                                                showFetchError(
                                                        "DingTalk returned empty device_code or"
                                                                + " scan URL"));
                                return;
                            }
                            byte[] imgBytes = fetchQrImage(scanUrl);
                            Image qrImage = new Image(new ByteArrayInputStream(imgBytes));
                            Platform.runLater(
                                    () -> {
                                        qrCodeView.setImage(qrImage);
                                        qrStatusLabel.setText(
                                                "Scan with DingTalk app to authorize");
                                        qrStatusLabel.getStyleClass().add("text-13-gray");
                                        getQrCodeBtn.setDisable(false);
                                        getQrCodeBtn.setText("Get DingTalk QR Code");
                                    });
                            polling.set(true);
                            startPolling(http, deviceCode);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "DingTalk QR code fetch failed", e);
                            Platform.runLater(
                                    () ->
                                            showFetchError(
                                                    "Failed to fetch QR code: " + e.getMessage()));
                        }
                    });
        }

        private void startPolling(HttpClient http, String deviceCode) {
            Thread.startVirtualThread(
                    () -> {
                        while (polling.get()) {
                            try {
                                Thread.sleep(3000);
                                if (!polling.get()) break;
                                String pollBody = "{\"device_code\":\"" + deviceCode + "\"}";
                                HttpRequest pollReq =
                                        HttpRequest.newBuilder()
                                                .uri(
                                                        URI.create(
                                                                DINGTALK_API_BASE
                                                                        + "/app/registration/poll"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(pollBody))
                                                .timeout(Duration.ofSeconds(10))
                                                .build();
                                HttpResponse<String> pollResp =
                                        http.send(pollReq, HttpResponse.BodyHandlers.ofString());
                                JsonNode pollData = JSON.readTree(pollResp.body());
                                String status = pollData.path("status").asString("WAITING");
                                if ("SUCCESS".equals(status)) {
                                    String cid = pollData.path("client_id").asString("");
                                    String csecret = pollData.path("client_secret").asString("");
                                    polling.set(false);
                                    Platform.runLater(
                                            () -> {
                                                clientIdField.setText(cid);
                                                clientSecretField.setText(csecret);
                                                qrStatusLabel.setText(
                                                        "✅ Authorization successful! Client ID and"
                                                                + " Secret filled in.");
                                                qrStatusLabel.setStyle(
                                                        "-fx-text-fill: #15a763; -fx-font-weight:"
                                                                + " 600; -fx-font-size: 13px;");
                                            });
                                    return;
                                } else if ("FAIL".equals(status)) {
                                    String reason = pollData.path("fail_reason").asString("");
                                    polling.set(false);
                                    Platform.runLater(
                                            () -> {
                                                qrStatusLabel.setText(
                                                        "❌ Authorization failed: " + reason);
                                                qrStatusLabel.getStyleClass().add("text-red-12");
                                            });
                                    return;
                                } else if ("EXPIRED".equals(status)) {
                                    polling.set(false);
                                    Platform.runLater(
                                            () -> {
                                                qrStatusLabel.setText(
                                                        "❌ QR code expired. Please try again.");
                                                qrStatusLabel.getStyleClass().add("text-red-12");
                                            });
                                    return;
                                }
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            } catch (Exception e) {
                                LOGGER.log(Level.FINE, "DingTalk poll error (ignored)", e);
                            }
                        }
                    });
        }

        private void showFetchError(String msg) {
            qrStatusLabel.setText("❌ " + msg);
            qrStatusLabel.getStyleClass().add("text-red-12");
            getQrCodeBtn.setDisable(false);
            getQrCodeBtn.setText("Get DingTalk QR Code");
        }

        private byte[] fetchQrImage(String url) throws Exception {
            QRCodeWriter barcodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix =
                    barcodeWriter.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return baos.toByteArray();
        }
    }
}
