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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.service.ToolService;
import ai.emailclaw.emailclaw.util.PlaywrightManager;
import ai.emailclaw.emailclaw.util.WebExtractUtils;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Network fetch tool.
 */
public class NetworkFetchTool extends BaseEmailclawTool {

    private static final Logger LOGGER = Logger.getLogger(NetworkFetchTool.class.getName());

    public NetworkFetchTool() {}

    /**
     * Detect a browser connection-level failure (browser process died or IPC pipe broken). Such
     * failures render the whole Playwright connection unusable, so the browser must be reset.
     */
    private static boolean isConnectionFailure(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("failed to read message")
                || m.contains("connection closed")
                || m.contains("target page, context or browser has been closed")
                || m.contains("target closed")
                || m.contains("browser has been closed")
                || m.contains("crash");
    }

    @Tool(
            name = BuiltInToolNames.WEB_FETCH,
            description =
                    "Fetch a web page and return its text content as simplified text (HTML stripped"
                        + " to readable text). Use for web pages only. For APIs, use http_request"
                        + " instead.")
    public String webFetch(
            @ToolParam(name = "url", description = "URL to browse and extract text from")
                    String url) {
        if (off(BuiltInToolNames.WEB_FETCH)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        if (url == null || url.isBlank()) {
            return "Error: url is required.";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);

        String guardCheck = checkGuard(BuiltInToolNames.WEB_FETCH, params);
        if (guardCheck != null) return guardCheck;

        WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200) {
            LOGGER.log(
                    Level.INFO,
                    "web_fetch fast HTTP path succeeded: url={0}, textLen={1}",
                    new Object[] {url, fast.text().length()});
            return fast.text();
        }

        String agentId =
                this.context.currentAgent != null ? this.context.currentAgent.getId() : "default";
        synchronized (BrowserAutomationTool.class) {
            PlaywrightManager.initPlaywrightIfNeeded(agentId);
        }
        Page page = null;
        try {
            page = PlaywrightManager.createEphemeralPage(agentId);
            LOGGER.log(Level.INFO, "Playwright preparing to open URL: {0}", url);
            page.navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.NETWORKIDLE)
                            .setTimeout(15000));
            return BrowserAutomationTool.extractVisibleTextFromPage(page);
        } catch (TimeoutError e) {
            try {
                if (page != null && !page.isClosed()) {
                    String text = BrowserAutomationTool.extractVisibleTextFromPage(page);
                    if (!text.isBlank()) return text;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Playwright text extraction failed", ex);
                if (isConnectionFailure(ex)) {
                    PlaywrightManager.reset(agentId);
                }
            }
            return "Page read timeout and failed to extract text.";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Playwright page read failed", e);
            return "Page read failed: " + e.getMessage();
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception closeErr) {
                    LOGGER.log(Level.FINE, "Failed to close ephemeral Playwright page", closeErr);
                }
            }
        }
    }
}
