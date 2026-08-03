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

import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Agent configuration view.
 *
 * <p>Centralized editing of ReAct, context, retry, rate limit, and tool security configuration.
 */
public class ConfigurationView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationView.class.getName());
    private final AppContext repository;
    private final VBox root = new VBox(14);
    private final TabPane tabPane = new TabPane();
    private AgentInfo currentAgent;
    private AgentConfiguration config;

    public ConfigurationView(AppContext repository, AgentInfo agent) {
        this.repository = repository;
        this.currentAgent = agent;
        this.config = repository.loadAgentConfig(agent.getId());
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        Label title = new Label("Configuration");
        title.getStyleClass().add("page-title");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs()
                .addAll(
                        buildReActTab(),
                        buildRetryTab(),
                        buildRateLimiterTab(),
                        buildContextTab(),
                        buildMemoryTab(),
                        buildSecurityTab());
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        Button saveBtn = new Button("Save Configuration");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setOnAction(e -> saveAll());
        root.getChildren().addAll(title, tabPane, saveBtn);
    }

    // ---------- ReAct Agent Tab ----------
    private final ComboBox<String> langField = new ComboBox<>();
    private final TextField tzField = new TextField();
    private final Spinner<Integer> maxIterField = new Spinner<>(1, 500, 100);
    private final ComboBox<String> shellExecutableField = new ComboBox<>();
    private final Spinner<Integer> shellTimeoutField = new Spinner<>(1, 600, 60);
    private final CheckBox autoContinue = new CheckBox("Auto continue on text-only");
    private final CheckBox autoTitle = new CheckBox("Auto-generate session titles");
    private final Spinner<Double> autoTitleTimeout = new Spinner<>(1.0, 120.0, 30.0, 1.0);
    private final TextField profileName = new TextField();
    private final ComboBox<String> localeField = new ComboBox<>();

    private Tab buildReActTab() {
        Tab tab = new Tab("ReAct Agent");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        langField.getItems().addAll("Chinese", "English", "Japanese", "Português (Brasil)");
        langField.setValue(config.getAgentLanguage());
        tzField.setText(config.getUserTimezone());
        maxIterField.getValueFactory().setValue(config.getMaxIterations());
        shellExecutableField.getItems().addAll("bash", "sh", "pwsh", "cmd.exe");
        shellExecutableField.setValue(
                config.getShellExecutable() == null || config.getShellExecutable().isBlank()
                        ? "bash"
                        : config.getShellExecutable());
        shellTimeoutField.getValueFactory().setValue(config.getShellCommandTimeout());
        autoContinue.setSelected(config.isAutoContinueOnTextOnly());
        autoTitle.setSelected(config.isAutoGenerateSessionTitle());
        autoTitleTimeout
                .getValueFactory()
                .setValue(config.getAutoGenerateSessionTitleTimeoutSeconds());
        profileName.setText(config.getProfileName() == null ? "" : config.getProfileName());
        localeField.getItems().addAll("zh", "en", "ja", "ru", "pt-BR");
        localeField.setValue(
                config.getConsoleLocale() == null || config.getConsoleLocale().isBlank()
                        ? "zh"
                        : config.getConsoleLocale());
        box.getChildren()
                .addAll(
                        row("Agent Language", langField),
                        row("User Timezone", tzField),
                        row("Max Iterations", maxIterField),
                        row("Shell Executable", shellExecutableField),
                        row("Shell Timeout (s)", shellTimeoutField),
                        row("Agent Profile Name", profileName),
                        row("Console Locale", localeField),
                        autoContinue,
                        autoTitle,
                        row("Title Timeout (s)", autoTitleTimeout));
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    // ---------- LLM Auto Retry Tab ----------
    private final CheckBox retryEnabled = new CheckBox("Enable LLM Auto Retry");
    private final Spinner<Integer> maxRetries = new Spinner<>(1, 20, 3);
    private final Spinner<Double> backoffBase = new Spinner<>(0.1, 60.0, 2.0, 0.5);
    private final Spinner<Double> backoffCap = new Spinner<>(0.5, 300.0, 30.0, 1.0);

    private Tab buildRetryTab() {
        Tab tab = new Tab("LLM Auto Retry");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        retryEnabled.setSelected(config.isLlmRetryEnabled());
        maxRetries.getValueFactory().setValue(config.getLlmMaxRetries());
        backoffBase.getValueFactory().setValue(config.getLlmBackoffBase());
        backoffCap.getValueFactory().setValue(config.getLlmBackoffCap());
        box.getChildren()
                .addAll(
                        retryEnabled,
                        row("Max Retries", maxRetries),
                        row("Backoff Base (s)", backoffBase),
                        row("Backoff Cap (s)", backoffCap));
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    // ---------- Rate Limiter Tab ----------
    private final Spinner<Integer> maxConcurrent = new Spinner<>(1, 32, 4);
    private final Spinner<Integer> maxQpm = new Spinner<>(0, 600, 0);
    private final Spinner<Double> ratePause = new Spinner<>(1.0, 600.0, 60.0, 1.0);
    private final Spinner<Double> rateJitter = new Spinner<>(0.0, 60.0, 5.0, 0.5);
    private final Spinner<Integer> acquireTimeout = new Spinner<>(1, 300, 30);

    private Tab buildRateLimiterTab() {
        Tab tab = new Tab("LLM Rate Limiter");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        maxConcurrent.getValueFactory().setValue(config.getLlmMaxConcurrent());
        maxQpm.getValueFactory().setValue(config.getLlmMaxQpm());
        ratePause.getValueFactory().setValue(config.getLlmRateLimitPause());
        rateJitter.getValueFactory().setValue(config.getLlmRateLimitJitter());
        acquireTimeout.getValueFactory().setValue(config.getLlmAcquireTimeout());
        box.getChildren()
                .addAll(
                        row("Max Concurrent", maxConcurrent),
                        row("Max QPM (0=off)", maxQpm),
                        row("Rate Limit Pause (s)", ratePause),
                        row("Jitter (s)", rateJitter),
                        row("Acquire Timeout (s)", acquireTimeout));
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    // ---------- Context Management Tab ----------
    private final ComboBox<String> ctxBackend = new ComboBox<>();
    private final Spinner<Integer> maxCtxLen = new Spinner<>(4096, 1048576, 131072, 8192);
    private final CheckBox planMode = new CheckBox("Plan Mode");

    private Tab buildContextTab() {
        Tab tab = new Tab("Context Mgmt");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        ctxBackend.getItems().addAll("light", "full");
        ctxBackend.setValue(config.getContextManagerBackend());
        maxCtxLen.getValueFactory().setValue(config.getMaxContextLength());
        planMode.setSelected(config.isPlanMode());
        box.getChildren()
                .addAll(row("Backend", ctxBackend), row("Max Context Length", maxCtxLen), planMode);
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    // ---------- Long-term Memory Tab ----------
    private final ComboBox<String> memBackend = new ComboBox<>();

    private Tab buildMemoryTab() {
        Tab tab = new Tab("Memory");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        memBackend.getItems().addAll("remelight", "none");
        memBackend.setValue(config.getMemoryManagerBackend());
        box.getChildren().addAll(row("Memory Backend", memBackend));
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    // ---------- Tool Execution Security Tab ----------
    private final ComboBox<String> permissionModeBox = new ComboBox<>();

    private Tab buildSecurityTab() {
        Tab tab = new Tab("Security");
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        permissionModeBox
                .getItems()
                .addAll("bypass", "default", "accept_edits", "explore", "dont_ask");
        permissionModeBox.setValue(config.getPermissionMode());
        permissionModeBox.setPrefWidth(150);
        Label desc =
                new Label(
                        "Permission mode controls how the engine evaluates tool execution"
                                + " requests.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        box.getChildren().addAll(row("Permission Mode", permissionModeBox), desc);
        tab.setContent(new ScrollPane(box));
        return tab;
    }

    private HBox row(String label, Node control) {
        HBox h = new HBox(8);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.setPrefWidth(180);
        l.getStyleClass().add("fw-600");
        h.getChildren().addAll(l, control);
        return h;
    }

    private void saveAll() {
        config.setAgentLanguage(langField.getValue());
        config.setUserTimezone(tzField.getText().trim());
        config.setMaxIterations(maxIterField.getValue());
        config.setShellExecutable(shellExecutableField.getValue());
        config.setShellCommandTimeout(shellTimeoutField.getValue());
        config.setAutoContinueOnTextOnly(autoContinue.isSelected());
        config.setAutoGenerateSessionTitle(autoTitle.isSelected());
        config.setAutoGenerateSessionTitleTimeoutSeconds(autoTitleTimeout.getValue());
        config.setProfileName(profileName.getText().trim());
        config.setConsoleLocale(localeField.getValue());
        config.setLlmRetryEnabled(retryEnabled.isSelected());
        config.setLlmMaxRetries(maxRetries.getValue());
        config.setLlmBackoffBase(backoffBase.getValue());
        config.setLlmBackoffCap(backoffCap.getValue());
        config.setLlmMaxConcurrent(maxConcurrent.getValue());
        config.setLlmMaxQpm(maxQpm.getValue());
        config.setLlmRateLimitPause(ratePause.getValue());
        config.setLlmRateLimitJitter(rateJitter.getValue());
        config.setLlmAcquireTimeout(acquireTimeout.getValue());
        config.setContextManagerBackend(ctxBackend.getValue());
        config.setMaxContextLength(maxCtxLen.getValue());
        config.setPlanMode(planMode.isSelected());
        config.setMemoryManagerBackend(memBackend.getValue());
        config.setPermissionMode(permissionModeBox.getValue());
        repository.saveAgentConfig(currentAgent.getId(), config);
        LOGGER.log(Level.INFO, "Save Agent configuration: agent={0}", currentAgent.getId());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        currentAgent = agent;
        config = repository.loadAgentConfig(agent.getId());
        refresh();
    }

    @Override
    public void refresh() {
        langField.setValue(config.getAgentLanguage());
        tzField.setText(config.getUserTimezone());
        maxIterField.getValueFactory().setValue(config.getMaxIterations());
        shellExecutableField.setValue(
                config.getShellExecutable() == null || config.getShellExecutable().isBlank()
                        ? "bash"
                        : config.getShellExecutable());
        shellTimeoutField.getValueFactory().setValue(config.getShellCommandTimeout());
        autoContinue.setSelected(config.isAutoContinueOnTextOnly());
        autoTitle.setSelected(config.isAutoGenerateSessionTitle());
        autoTitleTimeout
                .getValueFactory()
                .setValue(config.getAutoGenerateSessionTitleTimeoutSeconds());
        profileName.setText(config.getProfileName() == null ? "" : config.getProfileName());
        localeField.setValue(
                config.getConsoleLocale() == null || config.getConsoleLocale().isBlank()
                        ? "zh"
                        : config.getConsoleLocale());
        permissionModeBox.setValue(config.getPermissionMode());
    }
}
