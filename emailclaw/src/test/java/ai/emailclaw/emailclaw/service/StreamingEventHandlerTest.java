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
package ai.emailclaw.emailclaw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.model.ChatMessagePart;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamingEventHandlerTest {

    private List<ChatMessagePart> emittedParts;
    private StreamCallback callback;
    private StreamingEventHandler handler;

    @BeforeEach
    void setUp() {
        emittedParts = new ArrayList<>();
        callback =
                new StreamCallback() {
                    @Override
                    public void onPart(ChatMessagePart part, boolean startsNew) {
                        emittedParts.add(part);
                    }

                    @Override
                    public void onCompleted(io.agentscope.core.message.Msg message) {}
                };
        handler =
                new StreamingEventHandler(
                        null, callback, "agent-1", "session-1", "provider-1", "model-1");
    }

    @Test
    @DisplayName("ModelCallEndEvent accumulates tokens and sets hasModelUsage to true")
    void testModelCallEndTokenAccumulation() {
        assertFalse(handler.hasModelUsage());
        assertEquals(0, handler.getTotalInputTokens());
        assertEquals(0, handler.getTotalOutputTokens());
        assertEquals(0, handler.getTotalCachedTokens());

        ChatUsage usage1 =
                ChatUsage.builder().inputTokens(100).outputTokens(50).cachedTokens(30).build();
        ModelCallEndEvent event1 = new ModelCallEndEvent("reply-1", usage1);

        handler.handleEvent(event1);

        assertTrue(handler.hasModelUsage());
        assertEquals(100, handler.getTotalInputTokens());
        assertEquals(50, handler.getTotalOutputTokens());
        assertEquals(30, handler.getTotalCachedTokens());

        ChatUsage usage2 =
                ChatUsage.builder().inputTokens(200).outputTokens(80).cachedTokens(50).build();
        ModelCallEndEvent event2 = new ModelCallEndEvent("reply-2", usage2);

        handler.handleEvent(event2);

        assertEquals(300, handler.getTotalInputTokens());
        assertEquals(130, handler.getTotalOutputTokens());
        assertEquals(80, handler.getTotalCachedTokens());
    }

    @Test
    @DisplayName("ToolResultDataDeltaEvent with TextBlock emits TOOL_RESULT part")
    void testToolResultDataDeltaTextBlock() {
        TextBlock textBlock = TextBlock.builder().text("Tool output text").build();
        ToolResultDataDeltaEvent event =
                new ToolResultDataDeltaEvent("reply-1", "call-1", "search", textBlock);

        handler.handleEvent(event);

        assertFalse(emittedParts.isEmpty());
        ChatMessagePart part = emittedParts.get(0);
        assertEquals(ChatMessagePart.TOOL_RESULT, part.getType());
        assertEquals("Tool output text", part.getText());
        assertEquals("call-1", part.getId());
    }

    @Test
    @DisplayName("ToolResultDataDeltaEvent with ImageBlock and URLSource emits IMAGE part")
    void testToolResultDataDeltaImageBlockUrl() {
        URLSource source = URLSource.builder().url("https://example.com/chart.png").build();
        ImageBlock imageBlock = ImageBlock.builder().source(source).build();
        ToolResultDataDeltaEvent event =
                new ToolResultDataDeltaEvent("reply-2", "call-2", "generate_chart", imageBlock);

        handler.handleEvent(event);

        assertFalse(emittedParts.isEmpty());
        ChatMessagePart part = emittedParts.get(0);
        assertEquals(ChatMessagePart.IMAGE, part.getType());
        assertEquals("https://example.com/chart.png", part.getText());
        assertEquals("call-2", part.getId());
    }

    @Test
    @DisplayName(
            "ToolResultDataDeltaEvent with ImageBlock and Base64Source emits IMAGE part with data"
                    + " URI")
    void testToolResultDataDeltaImageBlockBase64() {
        Base64Source source =
                Base64Source.builder()
                        .mediaType("image/png")
                        .data("iVBORw0KGgoAAAANSUhEUg==")
                        .build();
        ImageBlock imageBlock = ImageBlock.builder().source(source).build();
        ToolResultDataDeltaEvent event =
                new ToolResultDataDeltaEvent("reply-3", "call-3", "capture_screen", imageBlock);

        handler.handleEvent(event);

        assertFalse(emittedParts.isEmpty());
        ChatMessagePart part = emittedParts.get(0);
        assertEquals(ChatMessagePart.IMAGE, part.getType());
        assertEquals("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==", part.getText());
        assertEquals("call-3", part.getId());
    }
}
