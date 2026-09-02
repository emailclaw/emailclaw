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
package ai.emailclaw.emailclaw.plugin.channel.emailclaw.ui;

import ai.emailclaw.emailclaw.plugin.channel.emailclaw.EmailMailPreset;
import ai.emailclaw.emailclaw.plugin.channel.emailclaw.EmailPresetRegistry;
import ai.emailclaw.emailclaw.plugin.channel.emailclaw.EmailclawChannelConfig;
import ai.emailclaw.emailclaw.plugin.channel.emailclaw.MailboxAccountConfig;
import ai.emailclaw.emailclaw.plugin.channel.emailclaw.OneTimePasswordAuth;
import ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Emailclaw configuration view provider supporting Multi-Agent Mailboxes.
 */
public class EmailclawChannelConfigViewProvider implements CustomConfigViewProvider {
    private static final Logger LOG =
            Logger.getLogger(EmailclawChannelConfigViewProvider.class.getName());

    @Override
    public String targetPluginId() {
        return ai.emailclaw.emailclaw.channel.ChannelIds.EMAILCLAW;
    }

    @Override
    public Node buildView(
            Map<String, Object> initialConfig,
            Consumer<Map<String, Object>> onSave,
            Runnable onCancel) {
        return new EmailclawMultiMailboxConfigPane(initialConfig, onSave, onCancel);
    }

    /**
     * The main window view that shows the Enable Plugin switch, Configured Mailboxes label,
     * + Add Mailbox button, and the list of mailboxes.
     */
    private static class EmailclawMultiMailboxConfigPane extends VBox {
        private final Map<String, Object> config;
        private final Consumer<Map<String, Object>> onSave;
        private final Runnable onCancel;
        private final SimpleToggleSwitch enableSwitch = new SimpleToggleSwitch();
        private final VBox mailboxCardsContainer = new VBox(8);
        private final Button saveButton = new Button("Save Global Configuration");

        private final List<MailboxAccountConfig> mailboxes = new ArrayList<>();

        public EmailclawMultiMailboxConfigPane(
                Map<String, Object> initialConfig,
                Consumer<Map<String, Object>> onSave,
                Runnable onCancel) {
            this.config = new HashMap<>(initialConfig);
            this.onSave = onSave;
            this.onCancel = onCancel;

            this.setPadding(new Insets(20));
            this.setSpacing(12);

            loadValues();

            HBox switchBox = new HBox(8, new Label("Enable Plugin"), enableSwitch);
            switchBox.setAlignment(Pos.CENTER_LEFT);

            Button addBtn = new Button("+ Add Mailbox");
            addBtn.getStyleClass().add("btn-primary");
            addBtn.setOnAction(e -> showAddOrEditDialog(null));

            HBox headerBox = new HBox(8, new Label("Configured Mailboxes:"), addBtn);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            headerBox.setPadding(new Insets(10, 0, 0, 0));

            ScrollPane scrollPane = new ScrollPane(mailboxCardsContainer);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            this.getChildren().addAll(switchBox, headerBox, scrollPane);

            saveButton.getStyleClass().add("btn-primary");
            saveButton.setOnAction(
                    e -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("enabled", enableSwitch.isSelected());
                        EmailclawChannelConfig.setMailboxes(result, this.mailboxes);
                        onSave.accept(result);
                    });

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setOnAction(
                    e -> {
                        if (onCancel != null) onCancel.run();
                    });

            HBox btnBox = new HBox(8, cancelBtn, saveButton);
            btnBox.setAlignment(Pos.BOTTOM_RIGHT);
            btnBox.setPadding(new Insets(20, 0, 0, 0));
            this.getChildren().add(btnBox);

            refreshMailboxList();
        }

        private void loadValues() {
            boolean isEnabled = (Boolean) this.config.getOrDefault("enabled", false);
            enableSwitch.setSelected(isEnabled);
            List<MailboxAccountConfig> mbs = EmailclawChannelConfig.getMailboxes(this.config);
            if (mbs != null) {
                mailboxes.addAll(mbs);
            }
        }

        private void refreshMailboxList() {
            mailboxCardsContainer.getChildren().clear();
            if (mailboxes.isEmpty()) {
                Label emptyLabel =
                        new Label("No mailboxes configured. Click \"+ Add Mailbox\" to add one.");
                emptyLabel.setStyle("-fx-text-fill: #9ca3af; -fx-padding: 10 0 10 0;");
                mailboxCardsContainer.getChildren().add(emptyLabel);
                return;
            }

            for (int i = 0; i < mailboxes.size(); i++) {
                final int index = i;
                MailboxAccountConfig mb = mailboxes.get(i);

                VBox card = new VBox(10);
                card.setStyle(
                        "-fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-padding: 12;"
                                + " -fx-background-color: #f9fafb;");

                // Top row: name, email, agent
                HBox topRow = new HBox(8);
                topRow.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(mb.name().isBlank() ? mb.emailAddress() : mb.name());
                nameLabel.setStyle("-fx-font-weight: bold;");

                Label emailLabel = new Label("(" + mb.emailAddress() + ")");
                emailLabel.setStyle("-fx-text-fill: #6b7280;");

                String displayAgentName = "Default";
                if (!mb.targetAgentId().isBlank()) {
                    displayAgentName = mb.targetAgentId();
                    var service =
                            ai.emailclaw.emailclaw.ui.plugin.PluginUIFactory.getAgentService();
                    if (service != null) {
                        displayAgentName =
                                service.list().stream()
                                        .filter(a -> a.getId().equals(mb.targetAgentId()))
                                        .map(a -> a.getName())
                                        .findFirst()
                                        .orElse(mb.targetAgentId());
                    }
                }

                Label agentLabel = new Label("Agent: " + displayAgentName);
                agentLabel.setStyle(
                        "-fx-background-color: #e0e7ff; -fx-text-fill: #3730a3; -fx-padding: 2 6 2"
                                + " 6; -fx-background-radius: 4; -fx-font-size: 11px;");

                topRow.getChildren().addAll(nameLabel, emailLabel, agentLabel);

                // Bottom row: toggle, spacer, edit, delete
                HBox bottomRow = new HBox(12);
                bottomRow.setAlignment(Pos.CENTER_LEFT);

                SimpleToggleSwitch enabledToggle = new SimpleToggleSwitch();
                enabledToggle.setSelected(mb.enabled());
                enabledToggle.setOnToggle(
                        sel -> {
                            MailboxAccountConfig updated = mb.withEnabled(sel);
                            mailboxes.set(index, updated);
                        });

                HBox spacer = new HBox();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button editBtn = new Button("Edit");
                editBtn.setOnAction(e -> showAddOrEditDialog(mb));

                Button deleteBtn = new Button("Delete");
                deleteBtn.setStyle("-fx-text-fill: #dc2626;");
                deleteBtn.setOnAction(
                        e -> {
                            mailboxes.remove(mb);
                            refreshMailboxList();
                        });

                bottomRow.getChildren().addAll(enabledToggle, spacer, editBtn, deleteBtn);

                card.getChildren().addAll(topRow, bottomRow);
                mailboxCardsContainer.getChildren().add(card);
            }
        }

        private void showAddOrEditDialog(MailboxAccountConfig existingConfig) {
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(this.getScene().getWindow());
            dialogStage.setTitle(existingConfig == null ? "Add Mailbox" : "Edit Mailbox");

            SingleMailboxConfigPane pane =
                    new SingleMailboxConfigPane(
                            existingConfig,
                            updatedConfig -> {
                                if (existingConfig != null) {
                                    int idx = mailboxes.indexOf(existingConfig);
                                    if (idx >= 0) {
                                        mailboxes.set(idx, updatedConfig);
                                    } else {
                                        mailboxes.add(updatedConfig);
                                    }
                                } else {
                                    // Check uniqueness
                                    boolean duplicate =
                                            mailboxes.stream()
                                                    .anyMatch(
                                                            m ->
                                                                    m.emailAddress()
                                                                            .equalsIgnoreCase(
                                                                                    updatedConfig
                                                                                            .emailAddress()));
                                    if (duplicate) {
                                        Alert a =
                                                new Alert(
                                                        Alert.AlertType.ERROR,
                                                        "Email address must be unique!");
                                        a.showAndWait();
                                        return;
                                    }
                                    mailboxes.add(updatedConfig);
                                }
                                refreshMailboxList();
                                dialogStage.close();
                            },
                            dialogStage::close);

            Scene scene = new Scene(pane, 800, 600);
            scene.getStylesheets().addAll(this.getScene().getStylesheets());
            dialogStage.setScene(scene);
            dialogStage.showAndWait();
        }
    }

    /**
     * The dialog content panel representing a single mailbox configuration.
     * This is an exact reproduction of the original panel adapted for MailboxAccountConfig.
     */
    private static class SingleMailboxConfigPane extends VBox {
        private static final String EMAILCLAW_CHANNEL_AGREEMENT =
"""
By using the Service, you acknowledge and agree to be bound by the following Emailclaw Channel Service Agreement:
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.
""";

        private final MailboxAccountConfig initialConfig;
        private final Consumer<MailboxAccountConfig> onSave;
        private final Runnable onCancel;

        private final TextField mailboxNameField = new TextField();
        private final javafx.scene.control.ComboBox<AgentOption> targetAgentIdComboBox =
                new javafx.scene.control.ComboBox<>();

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

        private static final Pattern EMAIL_PATTERN =
                Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        public SingleMailboxConfigPane(
                MailboxAccountConfig initialConfig,
                Consumer<MailboxAccountConfig> onSave,
                Runnable onCancel) {
            this.initialConfig = initialConfig;
            this.onSave = onSave;
            this.onCancel = onCancel;
            this.setPadding(new Insets(12, 16, 16, 16));

            Node content = buildContent();
            VBox.setVgrow(content, Priority.ALWAYS);
            this.getChildren().add(content);

            saveButton = new Button(initialConfig == null ? "Add Mailbox" : "Update Mailbox");
            saveButton.getStyleClass().add("btn-primary");
            saveButton.setOnAction(
                    e -> {
                        if (validateForm() == null) {
                            handleSave();
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
            btnBox.setPadding(new Insets(12, 0, 0, 0));
            this.getChildren().add(btnBox);

            loadValues(initialConfig);
            bindValidationListeners();
            refreshSaveState();
        }

        private Node buildContent() {
            VBox root = new VBox(8);
            root.setPadding(Insets.EMPTY);

            mailboxNameField.setPromptText("E.g., Support Mailbox");
            targetAgentIdComboBox.setPromptText("Select Target Agent");
            targetAgentIdComboBox.setMaxWidth(Double.MAX_VALUE);

            // Populate agents
            targetAgentIdComboBox.getItems().clear();
            targetAgentIdComboBox.getItems().add(new AgentOption("", "Default Agent"));
            var agentService = ai.emailclaw.emailclaw.ui.plugin.PluginUIFactory.getAgentService();
            if (agentService != null) {
                for (var agent : agentService.list()) {
                    targetAgentIdComboBox
                            .getItems()
                            .add(new AgentOption(agent.getId(), agent.getName()));
                }
            }
            targetAgentIdComboBox.getSelectionModel().selectFirst();

            Node generalInfoSection =
                    section(
                            "General Info",
                            field("Mailbox Name (Optional)", mailboxNameField),
                            field("Target Agent", targetAgentIdComboBox));

            ToggleGroup modeGroup = new ToggleGroup();
            ownEmailRadio.setToggleGroup(modeGroup);
            sysEmailRadio.setToggleGroup(modeGroup);
            ownEmailRadio.setSelected(true);
            VBox modeBox = new VBox(6, ownEmailRadio, sysEmailRadio);
            modeBox.setPadding(new Insets(10, 0, 6, 0));

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
            scrollPane.setStyle("-fx-background-color: transparent;");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            validationHint.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            validationHint.setWrapText(true);

            root.getChildren().addAll(generalInfoSection, modeBox, scrollPane, validationHint);
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
            autoPresetHint.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px;");
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
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
            autoPresetHintSystem.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px;");
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
            label.setStyle("-fx-font-weight: bold;");
            return new VBox(4, label, control);
        }

        private VBox section(String title, Node... children) {
            Label head = new Label(title);
            head.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            VBox box = new VBox(8);
            box.getChildren().add(head);
            box.getChildren().addAll(children);
            box.setStyle(
                    "-fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-padding: 10 12 12 12;");
            return box;
        }

        private void loadValues(MailboxAccountConfig config) {
            if (config == null) return;
            mailboxNameField.setText(nvl(config.name()));

            String targetId = nvl(config.targetAgentId());
            targetAgentIdComboBox.getItems().stream()
                    .filter(opt -> opt.id().equals(targetId))
                    .findFirst()
                    .ifPresent(opt -> targetAgentIdComboBox.getSelectionModel().select(opt));

            emailAddressField.setText(nvl(config.emailAddress()));
            passwordField.setText(nvl(config.emailPassword()));
            imapHostField.setText(nvl(config.effectiveImapHost()));
            imapPortSpinner
                    .getValueFactory()
                    .setValue(config.effectiveImapPort() <= 0 ? 993 : config.effectiveImapPort());
            imapSslCheck.setSelected(config.effectiveImapSsl());
            imapStartTlsCheck.setSelected(config.effectiveImapStartTls());
            smtpHostField.setText(nvl(config.effectiveSmtpHost()));
            smtpPortSpinner
                    .getValueFactory()
                    .setValue(config.effectiveSmtpPort() <= 0 ? 465 : config.effectiveSmtpPort());
            smtpSslCheck.setSelected(config.effectiveSmtpSsl());
            smtpStartTlsCheck.setSelected(config.effectiveSmtpStartTls());
            pollSecondsSpinner
                    .getValueFactory()
                    .setValue(
                            config.pollIntervalSeconds() <= 0 ? 30 : config.pollIntervalSeconds());
            allowSendersArea.setText(String.join("\n", config.allowlistSenders()));
            applyPresetForEmail(emailAddressField.getText(), false);

            sysRegistrationEmailField.setText("");
            sysOneTimePasswordField.setText("");
            sysPollSecondsSpinner
                    .getValueFactory()
                    .setValue(
                            config.pollIntervalSeconds() <= 0 ? 30 : config.pollIntervalSeconds());

            boolean isSystemEmail = config.emailAddress().endsWith("@emailclaw.email");
            if (isSystemEmail) {
                sysEmailRadio.setSelected(true);
                sysAllocatedEmailField.setText(config.emailAddress());
                sysAllocatedPasswordField.setText(config.emailPassword());
                if (!config.allowlistSenders().isEmpty()) {
                    sysRegistrationEmailField.setText(config.allowlistSenders().get(0));
                    sysAllowSendersArea.setText(config.allowlistSenders().get(0));
                }
            } else {
                ownEmailRadio.setSelected(true);
            }
            updateAllocatedAccountVisibility();
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
            validationHint.setText(valid ? "" : error);
            updateAllocatedAccountVisibility();
        }

        private void updateAllocatedAccountVisibility() {
            String allocatedEmail = sysAllocatedEmailField.getText();
            String allocatedPwd = sysAllocatedPasswordField.getText();
            boolean hasEmail = allocatedEmail != null && !allocatedEmail.isBlank();
            boolean hasPwd = allocatedPwd != null && !allocatedPwd.isBlank();
            if (sysAllocatedEmailBox != null) {
                sysAllocatedEmailBox.setVisible(hasEmail);
                sysAllocatedEmailBox.setManaged(hasEmail);
            }
            if (sysAllocatedPasswordBox != null) {
                sysAllocatedPasswordBox.setVisible(hasPwd);
                sysAllocatedPasswordBox.setManaged(hasPwd);
            }
            if (sysAllocatedAccountSection != null) {
                sysAllocatedAccountSection.setVisible(hasEmail || hasPwd);
                sysAllocatedAccountSection.setManaged(hasEmail || hasPwd);
            }
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

        private String getSelectedTargetAgentId() {
            return targetAgentIdComboBox.getValue() != null
                    ? targetAgentIdComboBox.getValue().id()
                    : "";
        }

        private void handleSave() {
            boolean sysMode = sysEmailRadio.isSelected();
            if (sysMode) {
                String registrantEmail = sysRegistrationEmailField.getText().trim();
                String oneTimePassword = sysOneTimePasswordField.getText();
                String allocatedEmail = sysAllocatedEmailField.getText();

                // If not already allocated, perform registration
                if (allocatedEmail == null || allocatedEmail.isBlank()) {
                    if (registrantEmail != null
                            && !registrantEmail.isBlank()
                            && oneTimePassword != null
                            && !oneTimePassword.isBlank()) {
                        OneTimePasswordAuth.EmailAndPassword authRes =
                                OneTimePasswordAuth.oneTimePasswordAuth(
                                        registrantEmail, oneTimePassword);
                        if (authRes != null) {
                            Platform.runLater(
                                    () -> {
                                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
                                        TextField emailField =
                                                new TextField(authRes.email().toUpperCase());
                                        emailField.setEditable(false);
                                        contentBox.getChildren().add(emailField);
                                        alert.getDialogPane().setContent(contentBox);
                                        alert.showAndWait();

                                        MailboxAccountConfig newConfig =
                                                new MailboxAccountConfig(
                                                        initialConfig != null
                                                                ? initialConfig.id()
                                                                : java.util
                                                                        .UUID
                                                                        .randomUUID()
                                                                        .toString(),
                                                        mailboxNameField.getText().trim(),
                                                        initialConfig != null
                                                                ? initialConfig.enabled()
                                                                : true,
                                                        authRes.email(),
                                                        authRes.password(),
                                                        "",
                                                        993,
                                                        true,
                                                        false,
                                                        "",
                                                        465,
                                                        true,
                                                        false,
                                                        getSelectedTargetAgentId(),
                                                        normalizeSenders(
                                                                sysAllowSendersArea.getText()),
                                                        sysPollSecondsSpinner.getValue());
                                        onSave.accept(newConfig);
                                    });
                        } else {
                            Platform.runLater(
                                    () ->
                                            validationHint.setText(
                                                    "Validation failed, please check registrant"
                                                            + " email and one-time password."));
                        }
                    }
                    return; // Wait for registration to complete
                } else {
                    // Already allocated (editing a sys mailbox)
                    MailboxAccountConfig newConfig =
                            new MailboxAccountConfig(
                                    initialConfig != null
                                            ? initialConfig.id()
                                            : java.util.UUID.randomUUID().toString(),
                                    mailboxNameField.getText().trim(),
                                    initialConfig != null ? initialConfig.enabled() : true,
                                    sysAllocatedEmailField.getText(),
                                    sysAllocatedPasswordField.getText(),
                                    "",
                                    993,
                                    true,
                                    false,
                                    "",
                                    465,
                                    true,
                                    false,
                                    getSelectedTargetAgentId(),
                                    normalizeSenders(sysAllowSendersArea.getText()),
                                    sysPollSecondsSpinner.getValue());
                    onSave.accept(newConfig);
                    return;
                }
            }

            applyPresetForEmail(emailAddressField.getText(), false);
            String email = emailAddressField.getText().trim();
            EmailMailPreset preset = EmailPresetRegistry.presetOf(email);

            String iHost = preset != null ? "" : imapHostField.getText().trim();
            int iPort = preset != null ? 993 : imapPortSpinner.getValue();
            boolean iSsl = preset != null || imapSslCheck.isSelected();
            boolean iTls = preset == null && imapStartTlsCheck.isSelected();

            String sHost = preset != null ? "" : smtpHostField.getText().trim();
            int sPort = preset != null ? 465 : smtpPortSpinner.getValue();
            boolean sSsl = preset != null || smtpSslCheck.isSelected();
            boolean sTls = preset == null && smtpStartTlsCheck.isSelected();

            MailboxAccountConfig newConfig =
                    new MailboxAccountConfig(
                            initialConfig != null
                                    ? initialConfig.id()
                                    : java.util.UUID.randomUUID().toString(),
                            mailboxNameField.getText().trim(),
                            initialConfig != null ? initialConfig.enabled() : true,
                            email,
                            passwordField.getText(),
                            iHost,
                            iPort,
                            iSsl,
                            iTls,
                            sHost,
                            sPort,
                            sSsl,
                            sTls,
                            getSelectedTargetAgentId(),
                            normalizeSenders(allowSendersArea.getText()),
                            pollSecondsSpinner.getValue());
            onSave.accept(newConfig);
        }

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
    }

    private static class SimpleToggleSwitch extends StackPane {
        private final Rectangle back = new Rectangle(36, 20);
        private final Circle thumb = new Circle(8);
        private boolean selected = false;
        private Consumer<Boolean> onToggle;

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

        public void setOnToggle(Consumer<Boolean> onToggle) {
            this.onToggle = onToggle;
        }

        public void setSelected(boolean sel) {
            boolean changed = this.selected != sel;
            this.selected = sel;
            back.setFill(sel ? Color.web("#4ade80") : Color.web("#e5e7eb"));
            StackPane.setAlignment(thumb, sel ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            if (changed && onToggle != null) {
                onToggle.accept(sel);
            }
        }
    }

    public record AgentOption(String id, String name) {
        @Override
        public String toString() {
            if (id.isBlank()) return name;
            return name + " (" + id + ")";
        }
    }
}
