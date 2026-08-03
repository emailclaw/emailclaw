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
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Browser automation tool.
 */
public class BrowserAutomationTool extends BaseEmailclawTool {

    private static final Logger LOGGER = Logger.getLogger(BrowserAutomationTool.class.getName());

    private static final long TOOL_MEDIA_MAX_BYTES = 10L * 1024L * 1024L;

    public BrowserAutomationTool() {}

    @Tool(
            name = BuiltInToolNames.BROWSER_USE,
            description = "Browser automation: open a URL and return the page text content")
    public String browserUse(
            @ToolParam(name = "url", description = "URL to browse and extract text from")
                    String url,
            @ToolParam(
                            name = "action",
                            description = "Optional action: extract_text or click",
                            required = false)
                    String action,
            @ToolParam(
                            name = "x",
                            description = "Page coordinate X for click action",
                            required = false)
                    Integer x,
            @ToolParam(
                            name = "y",
                            description = "Page coordinate Y for click action",
                            required = false)
                    Integer y,
            @ToolParam(
                            name = "selector",
                            description = "Optional CSS selector for click action",
                            required = false)
                    String selector) {
        if (off(BuiltInToolNames.BROWSER_USE)) {
            return BuiltInToolNames.TOOL_DISABLED_MESSAGE;
        }
        if (url == null || url.isBlank()) {
            return "Error: url is required.";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        if (action != null) params.put("action", action);
        if (x != null) params.put("x", x);
        if (y != null) params.put("y", y);
        if (selector != null) params.put("selector", selector);

        String guardCheck = checkGuard(BuiltInToolNames.BROWSER_USE, params);
        if (guardCheck != null) return guardCheck;

        WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200) {
            LOGGER.log(
                    Level.INFO,
                    "browser_use fast HTTP path succeeded: url={0}, textLen={1}",
                    new Object[] {url, fast.text().length()});
            return fast.text();
        }

        String agentId =
                this.context.currentAgent != null ? this.context.currentAgent.getId() : "default";
        synchronized (BrowserAutomationTool.class) {
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
            if (isClickAction(action, x, y, selector)) {
                LOGGER.log(
                        Level.INFO,
                        "Preparing to execute click action: selector={0}, x={1}, y={2}",
                        new Object[] {selector, x, y});
                if (selector != null && !selector.isBlank()) {
                    page.locator(selector).first().click();
                } else if (x != null && y != null) {
                    page.mouse().click(x, y);
                }
                page.waitForLoadState(
                        LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
            }
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
            return "Page read timeout and failed to extract text.";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Playwright page read failed", e);
            return "Page read failed: " + e.getMessage();
        }
    }

    @Tool(
            name = BuiltInToolNames.BROWSER_USE_ENHANCED,
            description =
                    "Enhanced browser automation with multimodal output. Actions: navigate, click,"
                        + " type, scroll, screenshot, hover, select_option, go_back, go_forward,"
                        + " wait, evaluate, extract_text, get_html. Returns text and/or screenshot"
                        + " image.")
    public ToolResultBlock browserUseEnhanced(
            @ToolParam(name = "url", description = "URL to open or navigate to", required = false)
                    String url,
            @ToolParam(
                            name = "action",
                            description =
                                    "Action: navigate, click, type, scroll, screenshot, hover,"
                                            + " select_option, go_back, go_forward, wait, evaluate,"
                                            + " extract_text, get_html",
                            required = false)
                    String action,
            @ToolParam(
                            name = "x",
                            description = "Page coordinate X for click action",
                            required = false)
                    Integer x,
            @ToolParam(
                            name = "y",
                            description = "Page coordinate Y for click action",
                            required = false)
                    Integer y,
            @ToolParam(
                            name = "selector",
                            description = "CSS selector for element-targeted actions",
                            required = false)
                    String selector,
            @ToolParam(
                            name = "text",
                            description = "Text to type or fill into a form field",
                            required = false)
                    String text,
            @ToolParam(
                            name = "key",
                            description = "Keyboard key to press (e.g. Enter, Tab, Escape)",
                            required = false)
                    String key,
            @ToolParam(
                            name = "scroll_x",
                            description = "Horizontal scroll distance in pixels",
                            required = false)
                    Integer scrollX,
            @ToolParam(
                            name = "scroll_y",
                            description = "Vertical scroll distance in pixels",
                            required = false)
                    Integer scrollY,
            @ToolParam(
                            name = "value",
                            description = "Option value for select_option action",
                            required = false)
                    String value,
            @ToolParam(
                            name = "wait_ms",
                            description = "Wait duration in milliseconds for wait action",
                            required = false)
                    Integer waitMs,
            @ToolParam(
                            name = "js_expression",
                            description = "JavaScript expression for evaluate action",
                            required = false)
                    String jsExpression) {
        if (off(BuiltInToolNames.BROWSER_USE_ENHANCED)) {
            return ToolResultBlock.text(BuiltInToolNames.TOOL_DISABLED_MESSAGE);
        }
        Map<String, Object> params = new HashMap<>();
        if (url != null) params.put("url", url);
        if (action != null) params.put("action", action);
        if (x != null) params.put("x", x);
        if (y != null) params.put("y", y);
        if (selector != null) params.put("selector", selector);
        if (text != null) params.put("text", text);
        if (key != null) params.put("key", key);
        if (scrollX != null) params.put("scroll_x", scrollX);
        if (scrollY != null) params.put("scroll_y", scrollY);
        if (value != null) params.put("value", value);
        if (waitMs != null) params.put("wait_ms", waitMs);
        if (jsExpression != null) params.put("js_expression", jsExpression);

        String guardCheck = checkGuard(BuiltInToolNames.BROWSER_USE_ENHANCED, params);
        if (guardCheck != null) {
            return ToolResultBlock.text(guardCheck);
        }

        BrowserAction normalizedAction = normalizeBrowserAction(action);
        if (canUseHttpFastPath(
                normalizedAction,
                url,
                x,
                y,
                selector,
                text,
                key,
                scrollX,
                scrollY,
                value,
                jsExpression)) {
            WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
            if (fast.ok()
                    && !fast.dynamicLikely()
                    && fast.text() != null
                    && fast.text().length() >= 200) {
                LOGGER.log(
                        Level.INFO,
                        "browser_use_enhanced fast HTTP path succeeded: url={0}, action={1},"
                                + " textLen={2}",
                        new Object[] {url, normalizedAction, fast.text().length()});
                return ToolResultBlock.text(fast.text());
            }
        }

        String agentId =
                this.context.currentAgent != null ? this.context.currentAgent.getId() : "default";
        synchronized (BrowserAutomationTool.class) {
            PlaywrightManager.initPlaywrightIfNeeded(agentId);
        }
        try {
            return executeBrowserUseEnhancedAction(
                    agentId,
                    normalizedAction,
                    url,
                    x,
                    y,
                    selector,
                    text,
                    key,
                    scrollX,
                    scrollY,
                    value,
                    waitMs,
                    jsExpression);
        } catch (TimeoutError e) {
            return recoverBrowserUseEnhancedAfterTimeout(normalizedAction, agentId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "browser_use_enhanced execution failed", e);
            return ToolResultBlock.error("Browser action failed: " + e.getMessage());
        }
    }

    private static boolean isClickAction(String action, Integer x, Integer y, String selector) {
        if ("click".equalsIgnoreCase(action)) return true;
        return action == null
                && ((x != null && y != null) || (selector != null && !selector.isBlank()));
    }

    private static BrowserAction normalizeBrowserAction(String action) {
        return BrowserAction.fromString(action);
    }

    private static boolean hasBrowserInteractionParams(
            Integer x,
            Integer y,
            String selector,
            String text,
            String key,
            Integer scrollX,
            Integer scrollY,
            String value,
            String jsExpression) {
        return (x != null && y != null)
                || (selector != null && !selector.isBlank())
                || (text != null && !text.isBlank())
                || (key != null && !key.isBlank())
                || scrollX != null
                || scrollY != null
                || (value != null && !value.isBlank())
                || (jsExpression != null && !jsExpression.isBlank());
    }

    private static boolean canUseHttpFastPath(
            BrowserAction action,
            String url,
            Integer x,
            Integer y,
            String selector,
            String text,
            String key,
            Integer scrollX,
            Integer scrollY,
            String value,
            String jsExpression) {
        if (url == null || url.isBlank()) return false;
        if (hasBrowserInteractionParams(
                x, y, selector, text, key, scrollX, scrollY, value, jsExpression)) return false;
        return action == BrowserAction.EXTRACT_TEXT || action == BrowserAction.NAVIGATE;
    }

    private static void navigateActivePage(String agentId, String url) {
        Page page = PlaywrightManager.getOrCreateActivePage(agentId);
        LOGGER.log(Level.INFO, "Playwright preparing to open URL: {0}", url);
        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(15000));
    }

    private static void ensurePageAtUrl(String agentId, Page page, String url) {
        if (url == null || url.isBlank()) return;
        String currentUrl = page.url();
        if (currentUrl == null || currentUrl.isBlank() || "about:blank".equals(currentUrl)) {
            navigateActivePage(agentId, url);
            return;
        }
        if (!currentUrl.equals(url)) {
            navigateActivePage(agentId, url);
        }
    }

    private static String extractVisibleTextFromPage(Page page) {
        Object textObj =
                page.evaluate(
                        "() => document.body ? (document.body.innerText ||"
                                + " document.body.textContent) : ''");
        return textObj == null ? "" : textObj.toString();
    }

    private static Object evaluateJavaScript(Page page, String jsExpression) {
        String expr = jsExpression.trim();
        if (!expr.startsWith("(") && !expr.startsWith("function")) {
            expr = "() => " + expr;
        }
        return page.evaluate(expr);
    }

    private ToolResultBlock executeBrowserUseEnhancedAction(
            String agentId,
            BrowserAction action,
            String url,
            Integer x,
            Integer y,
            String selector,
            String text,
            String key,
            Integer scrollX,
            Integer scrollY,
            String value,
            Integer waitMs,
            String jsExpression) {
        Page page = PlaywrightManager.getOrCreateActivePage(agentId);
        if (action == null) return ToolResultBlock.error("Invalid action");
        switch (action) {
            case NAVIGATE:
                if (url == null || url.isBlank())
                    return ToolResultBlock.error("url is required for navigate action.");
                navigateActivePage(agentId, url);
                return ToolResultBlock.text(
                        "Navigated to " + page.url() + "\n\n" + extractVisibleTextFromPage(page));
            case EXTRACT_TEXT:
                ensurePageAtUrl(agentId, page, url);
                if (url == null || url.isBlank()) {
                    if ("about:blank".equals(page.url()))
                        return ToolResultBlock.error(
                                "url is required when no browser page is open.");
                }
                return ToolResultBlock.text(extractVisibleTextFromPage(page));
            case GET_HTML:
                ensurePageAtUrl(agentId, page, url);
                if (url == null || url.isBlank()) {
                    if ("about:blank".equals(page.url()))
                        return ToolResultBlock.error(
                                "url is required when no browser page is open.");
                }
                return ToolResultBlock.text(page.content());
            case CLICK:
                ensurePageAtUrl(agentId, page, url);
                if (selector != null && !selector.isBlank()) {
                    page.locator(selector).first().click();
                } else if (x != null && y != null) {
                    page.mouse().click(x, y);
                } else {
                    return ToolResultBlock.error(
                            "click requires selector or both x and y coordinates.");
                }
                page.waitForLoadState(
                        LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                return ToolResultBlock.text("Click completed. Current URL: " + page.url());
            case TYPE:
                ensurePageAtUrl(agentId, page, url);
                if (text != null && !text.isBlank()) {
                    if (selector != null && !selector.isBlank()) {
                        page.locator(selector).first().fill(text);
                    } else {
                        page.keyboard().type(text);
                    }
                } else if (key != null && !key.isBlank()) {
                    if (selector != null && !selector.isBlank()) {
                        page.locator(selector).first().press(key);
                    } else {
                        page.keyboard().press(key);
                    }
                } else {
                    return ToolResultBlock.error("type requires text or key.");
                }
                return ToolResultBlock.text("Type completed. Current URL: " + page.url());
            case SCROLL:
                ensurePageAtUrl(agentId, page, url);
                int deltaX = scrollX == null ? 0 : scrollX;
                int deltaY = scrollY == null ? 0 : scrollY;
                page.mouse().wheel(deltaX, deltaY);
                return ToolResultBlock.text(
                        "Scrolled by (" + deltaX + ", " + deltaY + "). Current URL: " + page.url());
            case SCREENSHOT:
                ensurePageAtUrl(agentId, page, url);
                return screenshotActivePage(page);
            case HOVER:
                ensurePageAtUrl(agentId, page, url);
                if (selector == null || selector.isBlank()) {
                    return ToolResultBlock.error("hover requires selector.");
                }
                page.locator(selector).first().hover();
                return ToolResultBlock.text("Hover completed on selector: " + selector);
            case SELECT_OPTION:
                ensurePageAtUrl(agentId, page, url);
                if (selector == null || selector.isBlank()) {
                    return ToolResultBlock.error("select_option requires selector.");
                }
                if (value == null || value.isBlank()) {
                    return ToolResultBlock.error("select_option requires value.");
                }
                page.locator(selector).first().selectOption(value);
                return ToolResultBlock.text(
                        "Selected option '"
                                + value
                                + "' on selector: "
                                + selector
                                + ". Current URL: "
                                + page.url());
            case GO_BACK:
                page.goBack(new Page.GoBackOptions().setTimeout(15000));
                return ToolResultBlock.text("Navigated back. Current URL: " + page.url());
            case GO_FORWARD:
                page.goForward(new Page.GoForwardOptions().setTimeout(15000));
                return ToolResultBlock.text("Navigated forward. Current URL: " + page.url());
            case WAIT:
                long sleepMs = waitMs == null ? 1000L : Math.max(0, waitMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolResultBlock.error("Wait interrupted.");
                }
                return ToolResultBlock.text("Waited " + sleepMs + " ms.");
            case EVALUATE:
                if (jsExpression == null || jsExpression.isBlank()) {
                    return ToolResultBlock.error("evaluate requires js_expression.");
                }
                ensurePageAtUrl(agentId, page, url);
                Object result = evaluateJavaScript(page, jsExpression);
                return ToolResultBlock.text(result == null ? "null" : result.toString());
            default:
                return ToolResultBlock.error("Unknown action: " + action);
        }
    }

    private ToolResultBlock recoverBrowserUseEnhancedAfterTimeout(
            BrowserAction action, String agentId) {
        if (action != BrowserAction.EXTRACT_TEXT && action != BrowserAction.NAVIGATE) {
            return ToolResultBlock.error("Browser action timed out.");
        }
        try {
            Page page = PlaywrightManager.getActivePageIfExists(agentId);
            if (page != null && !page.isClosed()) {
                String text = extractVisibleTextFromPage(page);
                if (!text.isBlank()) {
                    return ToolResultBlock.text(text);
                }
            }
        } catch (Exception ignored) {
        }
        return ToolResultBlock.error("Page load timed out and no text could be extracted.");
    }

    private ToolResultBlock screenshotActivePage(Page page) {
        byte[] png = page.screenshot();
        if (png == null || png.length == 0) {
            return ToolResultBlock.error("Screenshot capture returned empty data.");
        }
        if (png.length > TOOL_MEDIA_MAX_BYTES) {
            return ToolResultBlock.error(
                    "Screenshot is too large. Max supported size is "
                            + (TOOL_MEDIA_MAX_BYTES / (1024 * 1024))
                            + " MB.");
        }
        Base64Source source =
                Base64Source.builder()
                        .mediaType("image/png")
                        .data(Base64.getEncoder().encodeToString(png))
                        .build();
        List<ContentBlock> output = new ArrayList<>();
        output.add(ImageBlock.builder().source(source).build());
        output.add(
                TextBlock.builder()
                        .text(
                                "Screenshot captured from "
                                        + page.url()
                                        + " ("
                                        + png.length
                                        + " bytes)")
                        .build());
        if (!activeModelSupportsImage()) {
            output.add(
                    TextBlock.builder()
                            .text(
                                    "Warning: active model may not support image multimodal input."
                                        + " Switch to a vision-capable model if analysis fails.")
                            .build());
        }
        return ToolResultBlock.of(output);
    }
}
