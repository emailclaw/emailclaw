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
package ai.emailclaw.emailclaw.channel.spi;

/**
 * Agent reply callback interface.
 *
 * <p>After the plugin submits a message via {@link ChannelContext#sendToAgent},
 * the framework notifies the plugin through this callback when the large model generates a reply.
 * The plugin sends the reply back to the corresponding channel in {@link #onCompleted}.
 */
public interface ReplyCallback {

    /**
     * Stream output intermediate chunk (can be ignored by some channels).
     *
     * @param fullTextSoFar Full text up to now
     */
    default void onChunk(String fullTextSoFar) {}

    /**
     * Model reply generation completed.
     *
     * @param fullText Full reply text
     */
    void onCompleted(String fullText);
}
