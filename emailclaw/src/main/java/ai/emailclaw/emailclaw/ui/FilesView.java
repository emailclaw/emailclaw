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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Workspace file browsing and editing view.
 *
 * <p>Supports Markdown file editing and real-time preview. Preview is rendered using commonmark-java,
 * supports GFM tables, strikethrough, autolinks.
 */
public class FilesView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(FilesView.class.getName());

    /**
     * Markdown file extensions supported by commonmark.
     */
    private static final Set<String> MARKDOWN_EXTS =
            Set.of(".md", ".markdown", ".mdown", ".mkd", ".txt");

    private final AppContext repository;

    private AgentInfo agent;

    private final BorderPane root = new BorderPane();

    private final ListView<Path> listView = new ListView<>();

    private final TextArea editor = new TextArea();

    private final WebView preview = new WebView();

    private final ToggleButton previewToggle =
            new ToggleButton("Edit") {

                {
                    setSelected(true);
                    updateToggleStyle(this);
                }
            };

    private final Label currentFile = new Label("Select a file to edit");

    private final BorderPane rightPanel = new BorderPane();

    private Path selected;

    /**
     * commonmark parser (thread-safe, reusable).
     */
    private final Parser mdParser;

    /**
     * commonmark HTML renderer (thread-safe, reusable).
     */
    private final HtmlRenderer mdRenderer;

    public FilesView(AppContext repository, AgentInfo agent) {
        this.repository = repository;
        this.agent = agent;
        // Initialize commonmark: register GFM extensions (tables, strikethrough, autolink)
        List<Extension> extensions =
                List.of(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        AutolinkExtension.create());
        this.mdParser = Parser.builder().extensions(extensions).build();
        this.mdRenderer = HtmlRenderer.builder().extensions(extensions).build();
        previewToggle
                .selectedProperty()
                .addListener(
                        (obs, o, n) -> {
                            previewToggle.setText(n ? "Edit" : "Preview");
                            updateToggleStyle(previewToggle);
                            refreshRight();
                        });
        buildUi();
        refresh();
    }

    // ==================== UI Construction ====================
    private void buildUi() {
        VBox page = new VBox(8);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        HBox head = new HBox(8);
        Label title = new Label("Workspace  /  Files");
        title.getStyleClass().add("page-title");
        Label workspace = new Label("Workspace: " + agent.getWorkspacePath());
        workspace.getStyleClass().add("muted");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button upload = new Button("Upload");
        Button download = new Button("Download");
        head.getChildren().addAll(title, workspace, spacer, upload, download);
        SplitPane split = new SplitPane();
        VBox left = new VBox(8);
        left.getStyleClass().add("card-lite");
        Label core = new Label("Core Files");
        listView.setCellFactory(
                v ->
                        new ListCell<>() {

                            @Override
                            protected void updateItem(Path item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(
                                        empty || item == null
                                                ? null
                                                : item.getFileName().toString());
                            }
                        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> openFile(n));
        left.getChildren().addAll(core, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        rightPanel.getStyleClass().add("card-lite");
        HBox top = new HBox(8, currentFile, previewToggle);
        HBox.setHgrow(currentFile, Priority.ALWAYS);
        Button reset = new Button("Reset");
        Button save = new Button("Save");
        top.getChildren().addAll(reset, save);
        reset.setOnAction(e -> openFile(selected));
        save.setOnAction(e -> saveFile());
        rightPanel.setTop(top);
        rightPanel.setCenter(editor);
        split.getItems().addAll(left, rightPanel);
        split.setDividerPositions(0.28);
        page.getChildren().addAll(head, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setCenter(page);
    }

    // ==================== File Operations ====================
    /**
     * Open file and display content in editor.
     * If preview mode is on, also update the rendering result in WebView.
     *
     * @param file File path to open
     */
    private void openFile(Path file) {
        selected = file;
        if (file == null) {
            currentFile.setText("Select a file to edit");
            editor.clear();
            return;
        }
        currentFile.setText(file.toString());
        try {
            editor.setText(Files.readString(file));
            LOGGER.log(Level.FINE, "Open file: {0}", file);
        } catch (IOException e) {
            editor.setText("Error: " + e.getMessage());
        }
        refreshRight();
    }

    /**
     * Save currently selected file.
     */
    private void saveFile() {
        if (selected == null) {
            return;
        }
        try {
            Files.writeString(selected, editor.getText());
            LOGGER.log(Level.INFO, "Save file: {0}", selected);
        } catch (IOException ignored) {
        }
    }

    // ==================== Preview Switch ====================
    /**
     * Refresh right panel: switch editor/preview view based on preview toggle state.
     */
    private void refreshRight() {
        if (previewToggle.isSelected()) {
            preview.getEngine().loadContent(renderMarkdownToHtml(editor.getText()));
            rightPanel.setCenter(preview);
        } else {
            rightPanel.setCenter(editor);
        }
    }

    // ==================== Markdown Rendering (commonmark-java) ====================
    /**
     * Render Markdown text to a complete HTML page.
     *
     * <p>Uses commonmark-java to parse Markdown, supports:
     * <ul>
     *   <li>Standard Markdown syntax (headings, lists, code blocks, links, images, etc.)</li>
     *   <li>GFM Tables ({@code commonmark-ext-gfm-tables})</li>
     *   <li>Strikethrough ({@code ~~text~~})</li>
     *   <li>Autolink (URL and email auto-recognition)</li>
     * </ul>
     *
     * @param markdown Markdown source text
     * @return Complete HTML page string
     */
    private String renderMarkdownToHtml(String markdown) {
        String text = markdown == null ? "" : markdown;
        // commonmark parse + render
        org.commonmark.node.Node document = mdParser.parse(text);
        StringWriter writer = new StringWriter();
        mdRenderer.render(document, writer);
        String bodyHtml = writer.toString();
        // Wrap as complete HTML page
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'/>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'/>");
        html.append("<style>");
        appendMarkdownStyles(html);
        html.append("</style></head><body>");
        html.append(bodyHtml);
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Append CSS styles for Markdown preview.
     *
     * @param sb Style string builder
     */
    private void appendMarkdownStyles(StringBuilder sb) {
        sb.append("body{");
        sb.append(
                "  font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica"
                        + " Neue',Arial,sans-serif;");
        sb.append("  padding:20px 28px;color:#1f2937;line-height:1.7;");
        sb.append("  max-width:900px;margin:0 auto;");
        sb.append("  font-size:15px;");
        sb.append("}");
        // Headings
        sb.append(
                "h1{font-size:1.8em;border-bottom:1px solid"
                        + " #e5e7eb;padding-bottom:8px;margin-top:1.2em;}");
        sb.append(
                "h2{font-size:1.45em;border-bottom:1px solid"
                        + " #f3f4f6;padding-bottom:6px;margin-top:1.1em;}");
        sb.append("h3{font-size:1.2em;margin-top:1em;}");
        // Code
        sb.append("pre{background:#f6f8fa;border:1px solid #e5e7eb;border-radius:8px;");
        sb.append("  padding:14px 16px;overflow-x:auto;font-size:13px;line-height:1.5;}");
        sb.append(
                "code{font-family:'JetBrains Mono','Fira"
                        + " Code',Consolas,monospace;font-size:0.9em;}");
        sb.append("pre code{background:none;padding:0;}");
        sb.append(
                ":not(pre)>code{background:#f3f4f6;padding:2px"
                        + " 6px;border-radius:4px;color:#d63384;}");
        // Blockquote
        sb.append("blockquote{border-left:4px solid #6366f1;margin:12px 0;padding:8px 16px;");
        sb.append("  background:#f8f9ff;border-radius:0 6px 6px 0;color:#4b5563;}");
        // Tables
        sb.append("table{border-collapse:collapse;width:100%;margin:16px 0;}");
        sb.append("th,td{border:1px solid #e5e7eb;padding:8px 12px;text-align:left;}");
        sb.append("th{background:#f9fafb;font-weight:600;}");
        sb.append("tr:nth-child(even){background:#f9fafb;}");
        // Links
        sb.append("a{color:#6366f1;text-decoration:none;}");
        sb.append("a:hover{text-decoration:underline;}");
        // Horizontal rule
        sb.append("hr{border:none;border-top:1px solid #e5e7eb;margin:24px 0;}");
        // Images
        sb.append("img{max-width:100%;border-radius:6px;margin:8px 0;}");
        // Lists
        sb.append("ul,ol{padding-left:24px;margin:8px 0;}");
        sb.append("li{margin:4px 0;}");
        // Strikethrough
        sb.append("del{color:#9ca3af;}");
    }

    /**
     * Update visual style of ToggleButton: highlighted + border when selected, normal when unselected.
     */
    static void updateToggleStyle(javafx.scene.control.ToggleButton btn) {
        if (btn.isSelected()) {
            btn.getStyleClass().add("btn-blue-63");
        } else {
            btn.getStyleClass().add("btn-ghost-border");
        }
    }

    // ==================== ViewPane Interface ====================
    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        this.agent = agent;
        refresh();
    }

    @Override
    public void refresh() {
        Path workspace = repository.workspaceFor(agent.getId());
        try {
            listView.getItems()
                    .setAll(
                            Files.list(workspace)
                                    .filter(Files::isRegularFile)
                                    .filter(
                                            p -> {
                                                String name =
                                                        p.getFileName().toString().toLowerCase();
                                                return MARKDOWN_EXTS.stream()
                                                        .anyMatch(name::endsWith);
                                            })
                                    .sorted()
                                    .toList());
        } catch (IOException e) {
            listView.getItems().clear();
        }
        if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }
    }
}
