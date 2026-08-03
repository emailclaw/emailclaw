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
package ai.emailclaw.emailclaw.ui.plugin;

import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * Frontend view provider SPI for complex plugin configuration panels.
 *
 * <p>This interface allows the underlying plugin system to be completely decoupled from the top-level JavaFX UI implementation.
 * For plugins requiring complex interactions (like QR scanning, polling, Tab switching, etc.), their underlying layer no longer contains `javafx.*` dependencies.
 * The frontend module (emailclaw-ui) uses this interface to provide and build the corresponding clean UI view for the targetPluginId().
 */
public interface CustomConfigViewProvider {

    /**
     * The target plugin ID corresponding to this view provider.
     * For example, returns "DingTalkPlugin".
     */
    String targetPluginId();

    /**
     * Build a clean view node (like VBox/Pane) containing business interactions, without the Dialog popup shell.
     *
     * @param initialConfig Initial configuration dictionary loaded from the underlying model
     * @param onSave        When the user completes configuration in the interface, or clicks via external Save button, callback to the main container with a Map containing the new configuration result
     * @param onCancel      Callback when user clicks cancel button, notifying the upper layer to close the popup
     * @return The built JavaFX node object
     */
    Node buildView(
            Map<String, Object> initialConfig,
            Consumer<Map<String, Object>> onSave,
            Runnable onCancel);
}
