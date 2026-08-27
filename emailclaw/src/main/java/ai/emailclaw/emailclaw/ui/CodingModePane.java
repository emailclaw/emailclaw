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
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Coding mode pane.
 *
 * <p>Provides file browsing, editing and Markdown preview functions, as an optional split pane for {@link ChatView}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>File tree browsing (TreeView)</li>
 *   <li>Code editing (TextArea)</li>
 *   <li>Markdown preview (WebView + commonmark)</li>
 *   <li>File creation, saving, project switching</li>
 * </ul>
 */
class CodingModePane {

    private static final Logger LOGGER = Logger.getLogger(CodingModePane.class.getName());

    /**
     * Coding mode state callback, used for communication with ChatView.
     */
    interface Callback {

        /**
         * Copy the selected file content to the chat input box.
         */
        void copyToChat(String markdown);

        /**
         * Get the current Agent's configuration.
         */
        AgentConfiguration getAgentConfiguration();

        /**
         * Save Agent configuration.
         */
        void saveAgentConfiguration(AgentConfiguration config);
    }

    private final ChatService chatService;

    private final Callback callback;

    // --- UI Components ---
    private final VBox pane = new VBox(0);

    private final BorderPane rightPanel = new BorderPane();

    private final TextArea editor = new TextArea();

    private final Label status = new Label("Coding Mode ready");

    private final Label currentFileLabel = new Label("No file selected");

    private final Label projectTitle = new Label("No Project");

    private final WebView preview = new WebView();

    private final ToggleButton previewToggle = new ToggleButton("Edit");

    private TreeView<Path> fileTree;

    private ComboBox<String> dirCombo;

    private HBox projectHeader;

    // --- State ---
    private Path dirRoot;

    private Path selectedFile;

    private Parser mdParser;

    private HtmlRenderer mdRenderer;

    private ProjectInfo currentProject;

    CodingModePane(ProjectInfo currentProject, ChatService chatService, Callback callback) {
        this.currentProject = currentProject;
        this.chatService = chatService;
        this.callback = callback;
        initMarkdownRenderer();
    }

    /**
     * Returns the built pane node.
     */
    VBox getPane() {
        return pane;
    }

    /**
     * Get the current project root directory.
     */
    Path getProjectRoot() {
        return dirRoot;
    }

    /**
     * Set the project root directory.
     */
    void setProjectRoot(Path root) {
        this.dirRoot = root;
        if (projectTitle != null && root != null) {
            projectTitle.setText(root.getFileName().toString());
        }
    }

    /**
     * Get the current editor text (for external reading).
     */
    String getEditorText() {
        return editor.getText();
    }

    /**
     * Get the currently selected file path.
     */
    Path getSelectedFile() {
        return selectedFile;
    }

    /**
     * Get the status label (for external text setting).
     */
    Label getStatusLabel() {
        return status;
    }

    // ======================== Initialization ========================
    private void initMarkdownRenderer() {
        List<Extension> extensions =
                List.of(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        AutolinkExtension.create());
        this.mdParser = Parser.builder().extensions(extensions).build();
        this.mdRenderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /**
     * Build coding mode pane UI.
     *
     * <p>After calling, the built VBox can be obtained through {@link #getPane()}.
     */
    void build() {
        previewToggle.setSelected(true);
        FilesView.updateToggleStyle(previewToggle);
        previewToggle.setText("Edit");
        previewToggle
                .selectedProperty()
                .addListener(
                        (obs, o, n) -> {
                            previewToggle.setText(n ? "Edit" : "Preview");
                            FilesView.updateToggleStyle(previewToggle);
                            refreshRight();
                        });
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.286);
        // --- Left: Explorer ---
        VBox left = buildLeftPane();
        // --- Right: Editor ---
        buildRightPane();
        split.getItems().addAll(left, rightPanel);
        pane.getChildren().add(split);
        VBox.setVgrow(split, Priority.ALWAYS);
    }

    private VBox buildLeftPane() {
        VBox left = new VBox(0);
        left.setStyle(
                "-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-width: 0 1 0"
                        + " 0;");
        projectHeader = new HBox(8);
        projectHeader.setAlignment(Pos.CENTER_LEFT);
        projectHeader.setStyle("-fx-padding: 8; -fx-background-color: #e5e7eb;");
        updateProjectHeaderUI();
        HBox filesHeader = new HBox(8);
        filesHeader.setAlignment(Pos.CENTER_LEFT);
        filesHeader.setStyle("-fx-padding: 4 8;");
        Label filesLabel = new Label("FILES");
        filesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #6b7280;");
        HBox fSpacer = new HBox();
        HBox.setHgrow(fSpacer, Priority.ALWAYS);
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshTree());
        Button newFileBtn = new Button("New File");
        newFileBtn.setOnAction(e -> createFile());
        filesHeader.getChildren().addAll(filesLabel, fSpacer, refreshBtn, newFileBtn);
        TreeItem<Path> rootItem =
                new PathTreeItem(dirRoot != null ? dirRoot : AppHomeConstants.HOME_RESOLVED);
        rootItem.setExpanded(true);
        if (dirRoot != null) {
            populateTree(rootItem, dirRoot);
        }
        fileTree = new TreeView<>(rootItem);
        fileTree.setShowRoot(true);
        fileTree.getStyleClass().addAll("bg-transparent", "border-trans");
        fileTree.setCellFactory(
                tv ->
                        new TreeCell<Path>() {

                            @Override
                            protected void updateItem(Path item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setGraphic(null);
                                    if (getDisclosureNode() != null) {
                                        getDisclosureNode().setOpacity(1.0);
                                        getDisclosureNode().setDisable(false);
                                    }
                                } else {
                                    setText(item.getFileName().toString());
                                    boolean isDir = Files.isDirectory(item);
                                    if (isDir) {
                                        Label folderIcon = new Label("\uD83D\uDCC0 ");
                                        folderIcon.setStyle(
                                                "-fx-text-fill: #fbbf24; -fx-padding: 0 4 0 0;");
                                        setGraphic(folderIcon);
                                        if (getTreeItem().getChildren().isEmpty()) {
                                            if (getDisclosureNode() != null) {
                                                getDisclosureNode().setOpacity(0.3);
                                                getDisclosureNode().setDisable(true);
                                            }
                                        } else {
                                            if (getDisclosureNode() != null) {
                                                getDisclosureNode().setOpacity(1.0);
                                                getDisclosureNode().setDisable(false);
                                            }
                                        }
                                    } else {
                                        Label fileIcon = new Label("\uD83D\uDCC1 ");
                                        fileIcon.setStyle(
                                                "-fx-text-fill: #9ca3af; -fx-padding: 0 4 0 0;");
                                        setGraphic(fileIcon);
                                        if (getDisclosureNode() != null) {
                                            getDisclosureNode().setOpacity(1.0);
                                            getDisclosureNode().setDisable(false);
                                        }
                                    }
                                }
                            }
                        });
        fileTree.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> openFileFromSelection(newItem));
        left.getChildren().addAll(projectHeader, filesHeader, fileTree);
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        return left;
    }

    public void onProjectChanged(ProjectInfo newProject) {
        this.currentProject = newProject;
        if (projectHeader != null) {
            updateProjectHeaderUI();
        }
    }

    private void updateProjectHeaderUI() {
        if (projectHeader == null) return;
        projectHeader.getChildren().clear();
        dirRoot = resolveDefaultProjectRoot();
        String defaultDirStr =
                dirRoot != null
                        ? dirRoot.toString()
                        : (currentProject != null
                                ? currentProject.getBaseDirectory()
                                : "No Project");
        if (currentProject != null
                && currentProject.getAdditionalDirs() != null
                && !currentProject.getAdditionalDirs().isEmpty()) {
            dirCombo = new ComboBox<>();
            dirCombo.getItems().add(currentProject.getBaseDirectory());
            dirCombo.getItems().addAll(currentProject.getAdditionalDirs().keySet());
            dirCombo.setValue(defaultDirStr);
            dirCombo.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(dirCombo, Priority.ALWAYS);
            dirCombo.valueProperty()
                    .addListener(
                            (obs, oldV, newV) -> {
                                if (newV != null) {
                                    dirRoot = Path.of(newV).toAbsolutePath().normalize();
                                    refreshTree();
                                }
                            });
            projectHeader.getChildren().add(dirCombo);
        } else {
            dirCombo = null;
            Label baseDirLabel = new Label(defaultDirStr);
            baseDirLabel.getStyleClass().add("fw-bold");
            projectHeader.getChildren().add(baseDirLabel);
        }
        refreshTree();
    }

    private void buildRightPane() {
        rightPanel.getStyleClass().add("bg-white");
        HBox editorToolbar = new HBox(8);
        editorToolbar.setAlignment(Pos.CENTER_LEFT);
        editorToolbar.setStyle(
                "-fx-padding: 8; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0;"
                        + " -fx-background-color: #f9fafb;");
        currentFileLabel.getStyleClass().add("fw-bold-slate");
        HBox eSpacer = new HBox();
        HBox.setHgrow(eSpacer, Priority.ALWAYS);
        Button copyToChatBtn = new Button("Copy to chat");
        copyToChatBtn.setOnAction(
                e -> {
                    if (selectedFile != null) {
                        String content = editor.getText();
                        String fileName = selectedFile.getFileName().toString();
                        String ext = "";
                        int i = fileName.lastIndexOf('.');
                        if (i > 0) {
                            ext = fileName.substring(i + 1);
                        }
                        String markdown = "```" + ext + "\n" + content + "\n```";
                        callback.copyToChat(markdown);
                    }
                });
        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> saveFile());
        saveBtn.getStyleClass().add("btn-green");
        editorToolbar
                .getChildren()
                .addAll(currentFileLabel, eSpacer, previewToggle, copyToChatBtn, saveBtn);
        editor.setPromptText("Select a file to edit");
        editor.getStyleClass().add("editor-input");
        HBox statusBar = new HBox(8);
        statusBar.setStyle("-fx-padding: 4 8; -fx-background-color: #007acc;");
        status.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
        statusBar.getChildren().add(status);
        rightPanel.setTop(editorToolbar);
        rightPanel.setCenter(editor);
        rightPanel.setBottom(statusBar);
    }

    // ======================== File Operations ========================
    private void openFileFromSelection(TreeItem<Path> item) {
        if (item == null || item.getValue() == null) {
            return;
        }
        Path file = item.getValue();
        if (Files.isRegularFile(file)) {
            openFile(file);
        }
    }

    private void openFile(Path file) {
        selectedFile = file;
        currentFileLabel.setText(file.toString());
        try {
            editor.setText(Files.readString(file));
            status.setText("Loaded: " + file.getFileName());
        } catch (IOException ex) {
            editor.setText("Unable to open file: " + ex.getMessage());
            status.setText("Open failed: " + ex.getMessage());
        }
        refreshRight();
    }

    /**
     * Refresh right panel: switch editor/preview view based on preview toggle.
     */
    void refreshRight() {
        if (previewToggle.isSelected()) {
            preview.getEngine().loadContent(renderMarkdownToHtml(editor.getText()));
            rightPanel.setCenter(preview);
        } else {
            rightPanel.setCenter(editor);
        }
    }

    private String renderMarkdownToHtml(String markdown) {
        String text = markdown == null ? "" : markdown;
        Node document = mdParser.parse(text);
        StringWriter writer = new StringWriter();
        mdRenderer.render(document, writer);
        String bodyHtml = writer.toString();
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'/>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'/>");
        html.append("<style>");
        html.append(
                "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        html.append(
                "  padding:20px 28px;color:#1f2937;line-height:1.7;max-width:900px;margin:0"
                        + " auto;font-size:15px;}");
        html.append(
                "pre{background:#f6f8fa;border:1px solid #e5e7eb;border-radius:8px;padding:14px"
                        + " 16px;overflow-x:auto;font-size:13px;line-height:1.5;}");
        html.append(
                "code{font-family:'JetBrains Mono','Fira"
                        + " Code',Consolas,monospace;font-size:0.9em;}");
        html.append("pre code{background:none;padding:0;}");
        html.append(
                ":not(pre)>code{background:#f3f4f6;padding:2px"
                        + " 6px;border-radius:4px;color:#d63384;}");
        html.append(
                "blockquote{border-left:4px solid #6366f1;margin:12px 0;padding:8px"
                        + " 16px;background:#f8f9ff;border-radius:0 6px 6px 0;color:#4b5563;}");
        html.append("table{border-collapse:collapse;width:100%;margin:16px 0;}");
        html.append("th,td{border:1px solid #e5e7eb;padding:8px 12px;text-align:left;}");
        html.append("th{background:#f9fafb;font-weight:600;}");
        html.append("tr:nth-child(even){background:#f9fafb;}");
        html.append("a{color:#6366f1;text-decoration:none;}");
        html.append("a:hover{text-decoration:underline;}");
        html.append("hr{border:none;border-top:1px solid #e5e7eb;margin:24px 0;}");
        html.append("img{max-width:100%;border-radius:6px;margin:8px 0;}");
        html.append("ul,ol{padding-left:24px;margin:8px 0;}");
        html.append("li{margin:4px 0;}");
        html.append("del{color:#9ca3af;}");
        html.append("</style></head><body>");
        html.append(bodyHtml);
        html.append("</body></html>");
        return html.toString();
    }

    private void saveFile() {
        if (selectedFile == null) {
            status.setText("Select a file to save first.");
            return;
        }
        try {
            Files.writeString(selectedFile, editor.getText());
            status.setText("Saved: " + selectedFile.getFileName());
            refreshTree();
        } catch (IOException ex) {
            status.setText("Save failed: " + ex.getMessage());
        }
    }

    private void createFile() {
        if (dirRoot == null || !Files.exists(dirRoot)) {
            status.setText("Select a coding project first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("new-file.txt");
        dialog.setTitle("New File");
        dialog.setHeaderText("Create a file inside the active coding project");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            Path file = dirRoot.resolve(result.get().trim());
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "");
                openFile(file);
                refreshTree();
                status.setText("Created: " + file.getFileName());
            } catch (IOException ex) {
                status.setText("Create failed: " + ex.getMessage());
            }
        }
    }

    // ======================== Project Operations ========================
    void switchProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Coding Project");
        Path initial = resolveDefaultProjectRoot();
        if (initial != null && Files.exists(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        } else {
            chooser.setInitialDirectory(AppHomeConstants.HOME_RESOLVED.toFile());
        }
        Window window = pane.getScene() != null ? pane.getScene().getWindow() : null;
        File selected = chooser.showDialog(window);
        if (selected != null) {
            dirRoot = selected.toPath().toAbsolutePath().normalize();
            projectTitle.setText(dirRoot.getFileName().toString());
            refreshTree();
        }
    }

    /**
     * Show select coding project dialog, returns whether successfully selected.
     */
    boolean showSelectProjectDialog(Window owner) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.initStyle(javafx.stage.StageStyle.UTILITY);
        dialog.setTitle("Select Coding Project");
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().setMinWidth(640);
        dialog.getDialogPane().setPrefHeight(320);
        dialog.getDialogPane().setMinHeight(320);
        Label description =
                new Label("Choose a project to inspect and edit, or use the default workspace.");
        description.getStyleClass().add("text-13-body");
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getChildren().add(description);
        Button defaultWorkspaceBtn = new Button("Use Default Workspace");
        defaultWorkspaceBtn.setMaxWidth(Double.MAX_VALUE);
        defaultWorkspaceBtn.setOnAction(
                e -> {
                    Path workspace = resolveDefaultProjectRoot();
                    if (workspace != null && Files.exists(workspace)) {
                        dirRoot = workspace;
                        dialog.setResult("default");
                        dialog.close();
                    } else {
                        status.setText("Default workspace is not available yet.");
                    }
                });
        Button chooseFolderBtn = new Button("Choose Folder");
        chooseFolderBtn.setMaxWidth(Double.MAX_VALUE);
        chooseFolderBtn.setOnAction(
                e -> {
                    DirectoryChooser dc = new DirectoryChooser();
                    dc.setTitle("Select Coding Project");
                    File dir = dc.showDialog(owner);
                    if (dir != null) {
                        dirRoot = dir.toPath().toAbsolutePath().normalize();
                        dialog.setResult("folder");
                        dialog.close();
                    }
                });
        content.getChildren().addAll(defaultWorkspaceBtn, chooseFolderBtn);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(button -> null);
        String result = dialog.showAndWait().orElse(null);
        return "default".equals(result) || "folder".equals(result);
    }

    private Path resolveDefaultProjectRoot() {
        if (currentProject != null
                && currentProject.getBaseDirectory() != null
                && !currentProject.getBaseDirectory().isBlank()) {
            LOGGER.info("currentProject.getBaseDirectory()===" + currentProject.getBaseDirectory());
            return Path.of(currentProject.getBaseDirectory()).toAbsolutePath().normalize();
        }
        return null;
    }

    // ======================== File Tree ========================
    void refreshTree() {
        if (fileTree == null) {
            return;
        }
        TreeItem<Path> rootItem =
                new PathTreeItem(dirRoot != null ? dirRoot : AppHomeConstants.HOME_RESOLVED);
        rootItem.setExpanded(true);
        if (dirRoot != null) {
            populateTree(rootItem, dirRoot);
        }
        fileTree.setRoot(rootItem);
    }

    private void populateTree(TreeItem<Path> parentItem, Path parentPath) {
        if (!Files.isDirectory(parentPath)) {
            return;
        }
        try (Stream<Path> children = Files.list(parentPath)) {
            children.filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(
                            Comparator.comparing((Path path) -> Files.isDirectory(path) ? 0 : 1)
                                    .thenComparing(
                                            path -> path.getFileName().toString().toLowerCase()))
                    .forEach(
                            path -> {
                                TreeItem<Path> child = new PathTreeItem(path);
                                if (Files.isDirectory(path)) {
                                    child.setExpanded(false);
                                    populateTree(child, path);
                                }
                                parentItem.getChildren().add(child);
                            });
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to build file tree: {0}", ex.getMessage());
        }
    }

    // ======================== Persistence ========================
    /**
     * Restore coding mode state from AgentConfiguration.
     */
    void restoreState(AgentConfiguration cfg) {
        dirRoot = null;
        Path fallback = resolveDefaultProjectRoot();
        if (fallback != null && Files.isDirectory(fallback)) {
            dirRoot = fallback;
        }
        if (dirCombo != null && dirRoot != null) {
            dirCombo.setValue(dirRoot.toString());
        }
        if (projectTitle != null && dirRoot != null) {
            projectTitle.setText(dirRoot.getFileName().toString());
        }
    }

    /**
     * Save coding mode state to AgentConfiguration.
     */
    void persistState(AgentConfiguration cfg) {
        // Handled by ProjectService now.
    }

    // ======================== Inner Classes ========================
    private static class PathTreeItem extends TreeItem<Path> {

        private final boolean isDirectory;

        public PathTreeItem(Path path) {
            super(path);
            this.isDirectory = Files.isDirectory(path);
        }

        @Override
        public boolean isLeaf() {
            return !isDirectory;
        }
    }
}
