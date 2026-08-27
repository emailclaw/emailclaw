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
package ai.emailclaw.emailclaw.channel.emailclaw.ui;

import ai.emailclaw.emailclaw.channel.emailclaw.EmailMailPreset;
import ai.emailclaw.emailclaw.channel.emailclaw.EmailPresetRegistry;
import ai.emailclaw.emailclaw.channel.emailclaw.EmailclawChannelConfig;
import ai.emailclaw.emailclaw.channel.emailclaw.OneTimePasswordAuth;
import ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Emailclaw configuration view provider.
 */
public class EmailclawConfigViewProvider implements CustomConfigViewProvider {
    private static final Logger LOG = Logger.getLogger(EmailclawConfigViewProvider.class.getName());
    private static final String EMAILCLAW_CHANNEL_AGREEMENT =
"""
By using the Service, you acknowledge and agree to be bound by the following Emailclaw Channel Service Agreement:
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.
""";

    @Override
    public String targetPluginId() {
        return ai.emailclaw.emailclaw.channel.ChannelIds.EMAILCLAW;
    }

    @Override
    public Node buildView(
            Map<String, Object> initialConfig,
            Consumer<Map<String, Object>> onSave,
            Runnable onCancel) {
        return new EmailclawConfigPane(initialConfig, onSave, onCancel);
    }

    private static class EmailclawConfigPane extends VBox {
        private final Map<String, Object> config;
        private final Consumer<Map<String, Object>> onSave;
        private final Runnable onCancel;

        private final TextField emailAddressField = new TextField();
        private final PasswordField passwordField = new PasswordField();
        private final TextField imapHostField = new TextField();
        private final Spinner<Integer> imapPortSpinner =
                new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 993));
        private final CheckBox imapSslCheck = new CheckBox("Use SSL");
        private final CheckBox imapStartTlsCheck = new CheckBox("Use STARTTLS");
        private final TextField smtpHostField = new TextField();
        private final Spinner<Integer> smtpPortSpinner =
                new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 465));
        private final CheckBox smtpSslCheck = new CheckBox("Use SSL");
        private final CheckBox smtpStartTlsCheck = new CheckBox("Use STARTTLS");
        private final Spinner<Integer> pollSecondsSpinner =
                new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 3600, 30));
        private final TextArea allowSendersArea = new TextArea();

        private VBox imapSection;
        private VBox smtpSection;

        private final Label autoPresetHint = new Label();
        private final Label validationHint = new Label();

        private final TextField sysRegistrationEmailField = new TextField();
        private final PasswordField sysOneTimePasswordField = new PasswordField();
        private final Spinner<Integer> sysPollSecondsSpinner =
                new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 3600, 30));
        private final TextArea sysAllowSendersArea = new TextArea();
        private final Label autoPresetHintSystem = new Label();
        private final CheckBox sysAgreementCheckBox = new CheckBox("Agree to Service Agreement");

        private final TextField sysAllocatedEmailField = new TextField();
        private final PasswordField sysAllocatedPasswordField = new PasswordField();
        private VBox sysAllocatedEmailBox;
        private VBox sysAllocatedPasswordBox;
        private VBox sysAllocatedAccountSection;

        private final RadioButton ownEmailRadio = new RadioButton("Bring my own Email account");
        private final RadioButton sysEmailRadio =
                new RadioButton("Use system provided Email account");

        private Button saveButton;
        private SimpleToggleSwitch enableSwitch;

        private static final Pattern EMAIL_PATTERN =
                Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        public EmailclawConfigPane(
                Map<String, Object> initialConfig,
                Consumer<Map<String, Object>> onSave,
                Runnable onCancel) {
            this.config = new java.util.HashMap<>(initialConfig);
            this.onSave = onSave;
            this.onCancel = onCancel;
            this.setPadding(new Insets(20));

            ai.emailclaw.emailclaw.model.ChannelInfo proxyChannel =
                    new ai.emailclaw.emailclaw.model.ChannelInfo(
                            ai.emailclaw.emailclaw.channel.ChannelIds.EMAILCLAW,
                            "Emailclaw",
                            false,
                            false);
            proxyChannel.setPluginConfig(this.config);
            proxyChannel.setEnabled((Boolean) this.config.getOrDefault("enabled", false));

            enableSwitch = new SimpleToggleSwitch();
            enableSwitch.setSelected(proxyChannel.isEnabled());
            enableSwitch.setDisable(true);

            HBox switchBox = new HBox(8, new Label("Enable Plugin"), enableSwitch);
            switchBox.setAlignment(Pos.CENTER_LEFT);

            this.getChildren().add(switchBox);
            Node content = buildContent();
            VBox.setVgrow(content, Priority.ALWAYS);
            this.getChildren().add(content);

            saveButton = new Button("Update Configuration");
            saveButton.getStyleClass().add("btn-primary");
            saveButton.setOnAction(
                    e -> {
                        if (validateForm() == null) {
                            if (saveValues(proxyChannel)) {
                                this.config.put("enabled", proxyChannel.isEnabled());
                                onSave.accept(this.config);
                            }
                        }
                    });
            Button cancelBtn = new Button("Cancel");
            cancelBtn.setOnAction(
                    e -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    });

            HBox btnBox = new HBox(8, cancelBtn, saveButton);
            btnBox.setAlignment(Pos.BOTTOM_RIGHT);
            btnBox.setPadding(new Insets(20, 0, 0, 0));
            this.getChildren().add(btnBox);

            loadValues(proxyChannel);
            bindValidationListeners();
            refreshSaveState();
        }

        private Node buildContent() {
            VBox root = new VBox(2);
            root.setPadding(new Insets(16, 0, 0, 0));
            ToggleGroup modeGroup = new ToggleGroup();
            ownEmailRadio.setToggleGroup(modeGroup);
            sysEmailRadio.setToggleGroup(modeGroup);
            ownEmailRadio.setSelected(true);
            VBox modeBox = new VBox(6, ownEmailRadio, sysEmailRadio);
            modeBox.setPadding(new Insets(0, 0, 6, 0));
            Node ownPane = buildOwnEmailTab();
            Node sysPane = buildSystemEmailTab();
            StackPane contentPane = new StackPane(ownPane);
            modeGroup
                    .selectedToggleProperty()
                    .addListener(
                            (obs, oldVal, newVal) -> {
                                if (newVal == sysEmailRadio) {
                                    contentPane.getChildren().setAll(sysPane);
                                } else {
                                    contentPane.getChildren().setAll(ownPane);
                                }
                                refreshSaveState();
                            });
            ScrollPane scrollPane = new ScrollPane(contentPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.getStyleClass().add("pane-transparent");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            validationHint.getStyleClass().add("text-red-12-sm");
            validationHint.setWrapText(true);
            root.getChildren().addAll(modeBox, scrollPane, validationHint);
            return root;
        }

        private Node buildOwnEmailTab() {
            VBox root = new VBox(10);
            root.setPadding(new Insets(16, 0, 0, 0));
            emailAddressField.setPromptText("example@domain.com");
            passwordField.setPromptText("Email password or app password");
            imapHostField.setPromptText("imap.example.com");
            smtpHostField.setPromptText("smtp.example.com");
            allowSendersArea.setPromptText("One sender email per line");
            allowSendersArea.setPrefRowCount(6);
            autoPresetHint.getStyleClass().add("text-blue-12");
            autoPresetHint.setWrapText(true);
            imapSection =
                    section(
                            "Incoming Server (IMAP)",
                            field("IMAP Host", imapHostField),
                            field("IMAP Port", imapPortSpinner),
                            imapSslCheck,
                            imapStartTlsCheck);
            smtpSection =
                    section(
                            "Outgoing Server (SMTP)",
                            field("SMTP Host", smtpHostField),
                            field("SMTP Port", smtpPortSpinner),
                            smtpSslCheck,
                            smtpStartTlsCheck);
            root.getChildren()
                    .addAll(
                            section(
                                    "Mailbox Account",
                                    field("Email Address", emailAddressField),
                                    autoPresetHint,
                                    field("Email Password", passwordField)),
                            imapSection,
                            smtpSection,
                            section(
                                    "Polling",
                                    field("Poll Interval (seconds)", pollSecondsSpinner)),
                            section(
                                    "Allowed Senders",
                                    new Label("Only emails from these senders will be processed."),
                                    allowSendersArea));
            return root;
        }

        private Node buildSystemEmailTab() {
            VBox root = new VBox(10);
            root.setPadding(new Insets(16, 0, 0, 0));

            Button viewAgreementBtn = new Button("View");
            viewAgreementBtn.setOnAction(
                    e -> {
                        javafx.scene.control.Alert alert =
                                new javafx.scene.control.Alert(
                                        javafx.scene.control.Alert.AlertType.INFORMATION);
                        alert.setTitle("Service Agreement");
                        alert.setHeaderText("Emailclaw Channel Service Agreement");

                        TextArea textArea = new TextArea(EMAILCLAW_CHANNEL_AGREEMENT);
                        textArea.setEditable(false);
                        textArea.setWrapText(true);
                        textArea.setPrefColumnCount(50);
                        textArea.setPrefRowCount(12);
                        alert.getDialogPane().setContent(textArea);

                        alert.showAndWait();
                    });
            HBox agreementBox = new HBox(8, sysAgreementCheckBox, viewAgreementBtn);
            agreementBox.setAlignment(Pos.CENTER_LEFT);

            sysRegistrationEmailField
                    .disableProperty()
                    .bind(sysAgreementCheckBox.selectedProperty().not());
            sysOneTimePasswordField
                    .disableProperty()
                    .bind(sysAgreementCheckBox.selectedProperty().not());

            sysRegistrationEmailField.setPromptText("example@domain.com");
            autoPresetHintSystem.setText("Email to otp@emailclaw.email to get one-time password.");
            autoPresetHintSystem.getStyleClass().add("text-blue-12");
            autoPresetHintSystem.setWrapText(true);
            sysOneTimePasswordField.setPromptText("One-time Password");
            sysAllowSendersArea.setPrefRowCount(6);
            sysAllowSendersArea.setDisable(true);

            sysAllocatedEmailField.setEditable(false);
            sysAllocatedPasswordField.setEditable(false);
            sysAllocatedEmailBox = field("Allocated Email Address", sysAllocatedEmailField);
            sysAllocatedPasswordBox = field("Allocated Email Password", sysAllocatedPasswordField);
            sysAllocatedAccountSection =
                    section("Mailbox Account", sysAllocatedEmailBox, sysAllocatedPasswordBox);

            Label agreementLabel = new Label(EMAILCLAW_CHANNEL_AGREEMENT);
            agreementLabel.setWrapText(true);
            agreementLabel.setMaxWidth(Double.MAX_VALUE);
            agreementLabel.setStyle(
                    "-fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-padding: 10 12 10 12;");
            agreementLabel.visibleProperty().bind(sysAgreementCheckBox.selectedProperty().not());
            agreementLabel.managedProperty().bind(sysAgreementCheckBox.selectedProperty().not());

            root.getChildren()
                    .addAll(
                            agreementBox,
                            agreementLabel,
                            section(
                                    "Sign-up for system provided mailbox",
                                    field("Registration Email", sysRegistrationEmailField),
                                    autoPresetHintSystem,
                                    field("One-time Password", sysOneTimePasswordField)),
                            sysAllocatedAccountSection,
                            section(
                                    "Polling",
                                    field("Poll Interval (seconds)", sysPollSecondsSpinner)),
                            section(
                                    "Allowed Senders",
                                    new Label("Only emails from these senders will be processed."),
                                    sysAllowSendersArea));
            return root;
        }

        private VBox field(String name, Node control) {
            Label label = new Label(name);
            label.getStyleClass().add("fw-600");
            return new VBox(4, label, control);
        }

        private VBox section(String title, Node... children) {
            Label head = new Label(title);
            head.getStyleClass().add("fw-700-14");
            VBox box = new VBox(8);
            box.getChildren().add(head);
            box.getChildren().addAll(children);
            box.setStyle(
                    "-fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-padding: 10 12 12 12;");
            return box;
        }

        private void loadValues(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            emailAddressField.setText(nvl(EmailclawChannelConfig.getEmailAddress(channel)));
            passwordField.setText(nvl(EmailclawChannelConfig.getEmailPassword(channel)));
            imapHostField.setText(nvl(EmailclawChannelConfig.getImapHost(channel)));
            int imapPort = EmailclawChannelConfig.getImapPort(channel);
            imapPortSpinner.getValueFactory().setValue(imapPort <= 0 ? 993 : imapPort);
            imapSslCheck.setSelected(EmailclawChannelConfig.isImapSsl(channel));
            imapStartTlsCheck.setSelected(EmailclawChannelConfig.isImapStartTls(channel));
            smtpHostField.setText(nvl(EmailclawChannelConfig.getSmtpHost(channel)));
            int smtpPort = EmailclawChannelConfig.getSmtpPort(channel);
            smtpPortSpinner.getValueFactory().setValue(smtpPort <= 0 ? 465 : smtpPort);
            smtpSslCheck.setSelected(EmailclawChannelConfig.isSmtpSsl(channel));
            smtpStartTlsCheck.setSelected(EmailclawChannelConfig.isSmtpStartTls(channel));
            int pollSeconds = EmailclawChannelConfig.getEmailPollIntervalSeconds(channel);
            pollSecondsSpinner.getValueFactory().setValue(pollSeconds <= 0 ? 30 : pollSeconds);
            allowSendersArea.setText(
                    String.join("\n", EmailclawChannelConfig.getEmailAllowlistSenders(channel)));
            applyPresetForEmail(emailAddressField.getText(), false);
            sysRegistrationEmailField.setText(
                    nvl(EmailclawChannelConfig.getRegistrantEmail(channel)));
            sysOneTimePasswordField.setText(
                    nvl(EmailclawChannelConfig.getOneTimePassword(channel)));
            sysPollSecondsSpinner.getValueFactory().setValue(pollSeconds <= 0 ? 30 : pollSeconds);
            sysAllowSendersArea.setText(sysRegistrationEmailField.getText());

            String allocatedEmail = EmailclawChannelConfig.getEmailAddress(channel);
            String allocatedPwd = EmailclawChannelConfig.getEmailPassword(channel);
            sysAllocatedEmailField.setText(nvl(allocatedEmail));
            sysAllocatedPasswordField.setText(nvl(allocatedPwd));
            boolean hasEmail = allocatedEmail != null && !allocatedEmail.isBlank();
            boolean hasPwd = allocatedPwd != null && !allocatedPwd.isBlank();
            sysAllocatedEmailBox.setVisible(hasEmail);
            sysAllocatedEmailBox.setManaged(hasEmail);
            sysAllocatedPasswordBox.setVisible(hasPwd);
            sysAllocatedPasswordBox.setManaged(hasPwd);
            sysAllocatedAccountSection.setVisible(hasEmail || hasPwd);
            sysAllocatedAccountSection.setManaged(hasEmail || hasPwd);

            if (EmailclawChannelConfig.isSysEmailMode(channel)) {
                sysEmailRadio.setSelected(true);
            } else {
                ownEmailRadio.setSelected(true);
            }
        }

        private void bindValidationListeners() {
            emailAddressField
                    .textProperty()
                    .addListener(
                            (obs, oldVal, newVal) -> {
                                applyPresetForEmail(newVal, true);
                                refreshSaveState();
                            });
            passwordField.textProperty().addListener((obs, oldVal, newVal) -> refreshSaveState());
            imapHostField.textProperty().addListener((obs, oldVal, newVal) -> refreshSaveState());
            smtpHostField.textProperty().addListener((obs, oldVal, newVal) -> refreshSaveState());
            allowSendersArea
                    .textProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            imapPortSpinner
                    .valueProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            smtpPortSpinner
                    .valueProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            pollSecondsSpinner
                    .valueProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            sysRegistrationEmailField
                    .textProperty()
                    .addListener(
                            (obs, oldVal, newVal) -> {
                                sysAllowSendersArea.setText(newVal);
                                refreshSaveState();
                            });
            sysOneTimePasswordField
                    .textProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            sysPollSecondsSpinner
                    .valueProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
            sysAgreementCheckBox
                    .selectedProperty()
                    .addListener((obs, oldVal, newVal) -> refreshSaveState());
        }

        private void refreshSaveState() {
            String error = validateForm();
            boolean valid = error == null;
            if (saveButton != null) saveButton.setDisable(!valid);
            if (enableSwitch != null) {
                enableSwitch.setDisable(!valid);
                enableSwitch.setSelected(valid);
            }
            validationHint.setText(valid ? "" : error);
        }

        private String validateForm() {
            if (sysEmailRadio.isSelected()) {
                if (!sysAgreementCheckBox.isSelected()) {
                    return "Please agree to the Service Agreement first.";
                }
                String allocatedEmail = sysAllocatedEmailField.getText();
                String allocatedPwd = sysAllocatedPasswordField.getText();
                if (allocatedEmail != null
                        && !allocatedEmail.isBlank()
                        && allocatedPwd != null
                        && !allocatedPwd.isBlank()) {
                    return null;
                }

                String email =
                        sysRegistrationEmailField.getText() == null
                                ? ""
                                : sysRegistrationEmailField.getText().trim();
                if (email.isBlank()) return "Please fill in Registration Email.";
                if (!isValidEmail(email)) return "Registration Email format is invalid.";
                String password =
                        sysOneTimePasswordField.getText() == null
                                ? ""
                                : sysOneTimePasswordField.getText().trim();
                if (password.isBlank()) return "Please fill in One-time Password.";
                return null;
            }
            String email =
                    emailAddressField.getText() == null ? "" : emailAddressField.getText().trim();
            if (email.isBlank()) return "Please fill in Email Address.";
            if (!isValidEmail(email)) return "Email Address format is invalid.";
            String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
            if (password.isBlank()) return "Please fill in Email Password or App Password.";
            EmailMailPreset preset = EmailPresetRegistry.presetOf(email);
            if (preset == null) {
                if (imapHostField.getText() == null || imapHostField.getText().trim().isBlank())
                    return "Please fill in IMAP Host.";
                if (smtpHostField.getText() == null || smtpHostField.getText().trim().isBlank())
                    return "Please fill in SMTP Host.";
            }
            List<String> senders = normalizeSenders(allowSendersArea.getText());
            if (senders.isEmpty()) return "Allowed Senders require at least one email address.";
            for (String sender : senders) {
                if (!isValidEmail(sender))
                    return "Allowed Senders contains an invalid email: " + sender;
            }
            return null;
        }

        private boolean saveValues(ai.emailclaw.emailclaw.model.ChannelInfo channel) {
            channel.setEnabled(enableSwitch.isSelected());
            boolean sysMode = sysEmailRadio.isSelected();
            EmailclawChannelConfig.setSysEmailMode(channel, sysMode);
            if (sysMode) {
                EmailclawChannelConfig.setRegistrantEmail(
                        channel, sysRegistrationEmailField.getText().trim());
                EmailclawChannelConfig.setOneTimePassword(
                        channel, sysOneTimePasswordField.getText());
                EmailclawChannelConfig.setEmailPollIntervalSeconds(
                        channel, sysPollSecondsSpinner.getValue());
                EmailclawChannelConfig.setEmailAllowlistSenders(
                        channel, normalizeSenders(sysAllowSendersArea.getText()));

                String registrantEmail = sysRegistrationEmailField.getText().trim();
                String oneTimePassword = sysOneTimePasswordField.getText();

                if (registrantEmail != null
                        && !registrantEmail.isBlank()
                        && oneTimePassword != null
                        && !oneTimePassword.isBlank()) {
                    String sysEmail =
                            OneTimePasswordAuth.oneTimePasswordAuth(
                                    channel, registrantEmail, oneTimePassword);
                    if (sysEmail != null) {
                        javafx.application.Platform.runLater(
                                () -> {
                                    javafx.scene.control.Alert alert =
                                            new javafx.scene.control.Alert(
                                                    javafx.scene.control.Alert.AlertType
                                                            .INFORMATION);
                                    alert.setTitle("Validation Successful");
                                    alert.setHeaderText("Registration Completed");

                                    VBox contentBox = new VBox(8);
                                    contentBox
                                            .getChildren()
                                            .add(
                                                    new Label(
                                                            "Send a new subject email to the"
                                                                    + " Agent's email address as"
                                                                    + " below:"));
                                    TextField emailField = new TextField(sysEmail.toUpperCase());
                                    emailField.setEditable(false);
                                    contentBox.getChildren().add(emailField);
                                    alert.getDialogPane().setContent(contentBox);

                                    alert.showAndWait();
                                });
                        return true;
                    } else {
                        javafx.application.Platform.runLater(
                                () -> {
                                    validationHint.setText(
                                            "Validation failed, please check registrant email and"
                                                    + " one-time password.");
                                });
                        return false;
                    }
                }
            }
            applyPresetForEmail(emailAddressField.getText(), false);
            EmailclawChannelConfig.setEmailAddress(channel, emailAddressField.getText().trim());
            EmailclawChannelConfig.setEmailPassword(channel, passwordField.getText());
            if (EmailclawChannelConfig.isPresetEmail(channel)) {
                EmailclawChannelConfig.normalizeEmailclawPluginConfig(channel);
            } else {
                EmailclawChannelConfig.setMailServerFromForm(
                        channel,
                        imapHostField.getText().trim(),
                        imapPortSpinner.getValue(),
                        imapSslCheck.isSelected(),
                        imapStartTlsCheck.isSelected(),
                        smtpHostField.getText().trim(),
                        smtpPortSpinner.getValue(),
                        smtpSslCheck.isSelected(),
                        smtpStartTlsCheck.isSelected());
            }
            EmailclawChannelConfig.setEmailPollIntervalSeconds(
                    channel, pollSecondsSpinner.getValue());
            EmailclawChannelConfig.setEmailAllowlistSenders(
                    channel, normalizeSenders(allowSendersArea.getText()));
            return true;
        }

        public record OneTimePasswordAndName(String name, String password) {}

        /**
         * Authentication result data
         */
        private record AuthResult(
                boolean success,
                String message,
                String userId,
                String username,
                String role,
                String accessToken,
                String refreshToken) {}

        private List<String> normalizeSenders(String text) {
            if (text == null || text.isBlank()) return new ArrayList<>();
            return Arrays.stream(text.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        private boolean isValidEmail(String value) {
            return value != null && EMAIL_PATTERN.matcher(value).matches();
        }

        private String nvl(String value) {
            return value == null ? "" : value;
        }

        private void applyPresetForEmail(String email, boolean force) {
            EmailMailPreset preset = EmailPresetRegistry.presetOf(email);
            if (preset == null) {
                autoPresetHint.setText("");
                setServerSectionsVisible(true);
                return;
            }
            autoPresetHint.setText(
                    "Detected "
                            + preset.displayName()
                            + " preset. Server settings are managed automatically.");
            setServerSectionsVisible(false);
            if (!force && !imapHostField.getText().isBlank() && !smtpHostField.getText().isBlank())
                return;
            imapHostField.setText(preset.imapHost());
            imapPortSpinner.getValueFactory().setValue(preset.imapPort());
            imapSslCheck.setSelected(preset.imapSsl());
            imapStartTlsCheck.setSelected(preset.imapStartTls());
            smtpHostField.setText(preset.smtpHost());
            smtpPortSpinner.getValueFactory().setValue(preset.smtpPort());
            smtpSslCheck.setSelected(preset.smtpSsl());
            smtpStartTlsCheck.setSelected(preset.smtpStartTls());
        }

        private void setServerSectionsVisible(boolean visible) {
            if (imapSection != null) {
                imapSection.setManaged(visible);
                imapSection.setVisible(visible);
            }
            if (smtpSection != null) {
                smtpSection.setManaged(visible);
                smtpSection.setVisible(visible);
            }
        }

        private class SimpleToggleSwitch extends StackPane {
            private final Rectangle back = new Rectangle(36, 20);
            private final Circle thumb = new Circle(8);
            private boolean selected = false;

            public SimpleToggleSwitch() {
                back.setArcWidth(20);
                back.setArcHeight(20);
                back.setFill(Color.web("#e5e7eb"));
                thumb.setFill(Color.WHITE);
                thumb.setEffect(new DropShadow(2, Color.gray(0, 0.3)));
                StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
                StackPane.setMargin(thumb, new Insets(0, 2, 0, 2));
                getChildren().addAll(back, thumb);
                setOnMouseClicked(
                        e -> {
                            if (!isDisabled()) setSelected(!selected);
                        });
                setCursor(Cursor.HAND);
                disableProperty().addListener((obs, oldV, newV) -> setOpacity(newV ? 0.5 : 1.0));
            }

            public boolean isSelected() {
                return selected;
            }

            public void setSelected(boolean sel) {
                this.selected = sel;
                back.setFill(sel ? Color.web("#4ade80") : Color.web("#e5e7eb"));
                StackPane.setAlignment(thumb, sel ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            }
        }
    }
}
