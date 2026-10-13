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
package ai.emailclaw.emailclaw.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebExtractUtilsTest {

    @Test
    @DisplayName("Content-Type classifiers accurately identify supported MIME types")
    void testContentTypeClassifiers() {
        assertTrue(WebExtractUtils.isHtmlContentType("text/html; charset=utf-8"));
        assertTrue(WebExtractUtils.isHtmlContentType("application/xhtml+xml"));
        assertFalse(WebExtractUtils.isHtmlContentType("application/rss+xml"));

        assertTrue(WebExtractUtils.isXmlOrFeedContentType("application/rss+xml; charset=utf-8"));
        assertTrue(WebExtractUtils.isXmlOrFeedContentType("application/atom+xml"));
        assertTrue(WebExtractUtils.isXmlOrFeedContentType("application/xml"));
        assertTrue(WebExtractUtils.isXmlOrFeedContentType("text/xml"));
        assertFalse(WebExtractUtils.isXmlOrFeedContentType("text/html"));

        assertTrue(WebExtractUtils.isJsonContentType("application/json; charset=utf-8"));
        assertTrue(WebExtractUtils.isJsonContentType("text/json"));
        assertFalse(WebExtractUtils.isJsonContentType("text/plain"));

        assertTrue(WebExtractUtils.isPlainTextContentType("text/plain; charset=utf-8"));
        assertTrue(WebExtractUtils.isPlainTextContentType("text/markdown"));
        assertTrue(WebExtractUtils.isPlainTextContentType("text/csv"));
        assertFalse(WebExtractUtils.isPlainTextContentType("application/pdf"));
    }

    @Test
    @DisplayName("extractFeedOrXmlText parses RSS channel and items into structured markdown")
    void testRssFeedExtraction() {
        String rssXml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                    <channel>
                        <title>TechCrunch</title>
                        <link>https://techcrunch.com</link>
                        <description>Startup and Technology News</description>
                        <item>
                            <title>OpenAI Releases New Model</title>
                            <link>https://techcrunch.com/article-1</link>
                            <pubDate>Mon, 12 Oct 2026 12:00:00 GMT</pubDate>
                            <description>OpenAI announced their new multimodal reasoning agent.</description>
                        </item>
                        <item>
                            <title>Anthropic Updates Claude</title>
                            <link>https://techcrunch.com/article-2</link>
                            <pubDate>Mon, 12 Oct 2026 14:00:00 GMT</pubDate>
                            <description>Anthropic shared improvements to tool use and coding.</description>
                        </item>
                    </channel>
                </rss>
                """;

        String extracted = WebExtractUtils.extractFeedOrXmlText(rssXml);

        assertTrue(extracted.contains("# TechCrunch"));
        assertTrue(extracted.contains("### OpenAI Releases New Model"));
        assertTrue(extracted.contains("https://techcrunch.com/article-1"));
        assertTrue(extracted.contains("OpenAI announced their new multimodal reasoning agent."));
        assertTrue(extracted.contains("### Anthropic Updates Claude"));
    }

    @Test
    @DisplayName("extractFeedOrXmlText extracts clean text from non-feed XML documents")
    void testGeneralXmlExtraction() {
        String genericXml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog>
                    <book id="bk101">
                        <author>Gambardella, Matthew</author>
                        <title>XML Developer's Guide</title>
                        <price>44.95</price>
                    </book>
                </catalog>
                """;

        String extracted = WebExtractUtils.extractFeedOrXmlText(genericXml);

        assertTrue(extracted.contains("Gambardella, Matthew"));
        assertTrue(extracted.contains("XML Developer's Guide"));
        assertTrue(extracted.contains("44.95"));
        assertFalse(extracted.contains("<catalog>"));
    }
}
