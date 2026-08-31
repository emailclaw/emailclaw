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

import ai.emailclaw.emailclaw.model.VoiceTranscriptionConfig;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class VoiceTranscriptionView implements ViewPane {
    /**
     * Voice transcription configuration view.
     */
    private static final Logger LOGGER = Logger.getLogger(VoiceTranscriptionView.class.getName());

    private final AppContext repository;
    private final VBox root = new VBox(16);
    private VoiceTranscriptionConfig config;
    private final ComboBox<String> audioMode = new ComboBox<>();
    private final ComboBox<String> transcriptionProvider = new ComboBox<>();

    public VoiceTranscriptionView(AppContext repository) {
        this.repository = repository;
        this.config = repository.loadVoiceTranscription();
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        Label title = new Label("Voice Transcription");
        title.getStyleClass().add("page-title");
        Label desc = new Label("Configure how audio messages are handled and transcribed.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        // Audio Mode
        VBox modeBox = new VBox(6);
        modeBox.getStyleClass().add("card");
        Label modeLabel = new Label("Audio Mode");
        modeLabel.getStyleClass().add("fw-700-16");
        Label modeDesc =
                new Label(
                        "Determines how incoming audio is processed. "
                                + "'auto' will attempt transcription if a provider is configured.");
        modeDesc.getStyleClass().add("muted");
        modeDesc.setWrapText(true);
        audioMode.getItems().addAll("auto", "transcribe", "passthrough", "disabled");
        audioMode.setValue(config.getAudioMode());
        modeBox.getChildren().addAll(modeLabel, modeDesc, audioMode);

        // Transcription Provider
        VBox providerBox = new VBox(6);
        providerBox.getStyleClass().add("card");
        Label provLabel = new Label("Transcription Provider");
        provLabel.getStyleClass().add("fw-700-16");
        Label provDesc =
                new Label("Select the speech-to-text service used for voice transcription.");
        provDesc.getStyleClass().add("muted");
        provDesc.setWrapText(true);
        transcriptionProvider.getItems().setAll("disabled", "whisper_api", "local_whisper");
        transcriptionProvider.setValue(
                transcriptionProvider.getItems().contains(config.getTranscriptionProvider())
                        ? config.getTranscriptionProvider()
                        : "disabled");
        providerBox.getChildren().addAll(provLabel, provDesc, transcriptionProvider);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setOnAction(
                e -> {
                    config.setAudioMode(audioMode.getValue());
                    config.setTranscriptionProvider(transcriptionProvider.getValue());
                    repository.saveVoiceTranscription(config);
                    LOGGER.log(
                            Level.INFO,
                            "Save voice transcription configuration: mode={0}, provider={1}",
                            new Object[] {
                                config.getAudioMode(), config.getTranscriptionProvider()
                            });
                });

        root.getChildren().addAll(title, desc, modeBox, providerBox, saveBtn);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        config = repository.loadVoiceTranscription();
        audioMode.setValue(config.getAudioMode());
        transcriptionProvider.setValue(
                transcriptionProvider.getItems().contains(config.getTranscriptionProvider())
                        ? config.getTranscriptionProvider()
                        : "disabled");
    }
}
