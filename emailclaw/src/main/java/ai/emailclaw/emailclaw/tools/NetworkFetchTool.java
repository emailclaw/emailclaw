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

    @Tool(
            description =
                    "Fetch a URL and return its content as simplified text (HTML stripped to"
                            + " readable text). Use for web pages only. For APIs, use http_request"
                            + " instead.")
    public String fetch_url(@ToolParam(name = "url", description = "URL to fetch") String url) {
        if (off(BuiltInToolNames.FETCH_URL)) {
            return BuiltInToolNames.TOOL_DISABLED_MESSAGE;
        }
        if (url == null || url.isBlank()) {
            return "Error: url is required.";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        String guardCheck = checkGuard(BuiltInToolNames.FETCH_URL, params);
        if (guardCheck != null) return guardCheck;

        WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200) {
            LOGGER.log(
                    Level.INFO,
                    "fetch_url succeeded via fast HTTP path: url={0}, textLen={1}",
                    new Object[] {url, fast.text().length()});
            return fast.text();
        }

        String agentId =
                this.context.currentAgent != null ? this.context.currentAgent.getId() : "default";
        synchronized (NetworkFetchTool.class) {
            PlaywrightManager.initPlaywrightIfNeeded(agentId);
        }
        Page page = PlaywrightManager.getOrCreateActivePage(agentId);
        try {
            LOGGER.log(Level.INFO, "Playwright preparing to open URL: {0}", url);
            page.navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.NETWORKIDLE)
                            .setTimeout(15000));
            Object textObj =
                    page.evaluate(
                            "() => document.body ? (document.body.innerText ||"
                                    + " document.body.textContent) : ''");
            return textObj == null ? "" : textObj.toString();
        } catch (TimeoutError e) {
            try {
                Object textObj =
                        page.evaluate(
                                "() => document.body ? (document.body.innerText ||"
                                        + " document.body.textContent) : ''");
                String text = textObj == null ? "" : textObj.toString();
                if (!text.isBlank()) return text;
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Playwright text extraction failed", ex);
            }
            return "Webpage load timeout and text extraction failed.";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Playwright webpage load failed", e);
            return "Webpage load failed: " + e.getMessage();
        }
    }
}
