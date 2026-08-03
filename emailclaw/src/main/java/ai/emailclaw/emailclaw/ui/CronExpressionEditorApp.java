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

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Java 25 JavaFX Cron Expression Visual Editor provided by senior architect
 * Supports two-way switching between manual input and visual GUI, plug and play.
 */
public class CronExpressionEditorApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create our custom Cron editor component
        var cronEditor = new CronExpressionEditor();

        // Root layout of the test page
        var root = new VBox(15);
        root.setPadding(new Insets(30));
        var titleLabel = new Label("Cron Job execution time:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        root.getChildren().addAll(titleLabel, cronEditor);

        var scene = new Scene(root, 650, 450);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Java 25 - Modern Cron Expression Editor");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
