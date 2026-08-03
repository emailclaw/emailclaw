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
 * Channel plugin runtime status snapshot.
 *
 * <p>The framework uses this object to perceive plugin health and displays it to the user in the UI.
 */
public record ChannelStatus(
        /** Current lifecycle phase */
        Phase phase,
        /** Human-readable status description (e.g. "Connected", "Polling", "Auth Failed", etc.) */
        String message) {
    public enum Phase {
        /** Registered but not initialized */
        REGISTERED,
        /** Initialized, waiting to start */
        INITIALIZED,
        /** Running */
        RUNNING,
        /** Stopped (can be restarted) */
        STOPPED,
        /** Running but has errors */
        ERROR
    }

    public static ChannelStatus registered() {
        return new ChannelStatus(Phase.REGISTERED, "Registered");
    }

    public static ChannelStatus initialized() {
        return new ChannelStatus(Phase.INITIALIZED, "Initialized, waiting to start");
    }

    public static ChannelStatus running(String message) {
        return new ChannelStatus(Phase.RUNNING, message);
    }

    public static ChannelStatus stopped() {
        return new ChannelStatus(Phase.STOPPED, "Stopped");
    }

    public static ChannelStatus error(String message) {
        return new ChannelStatus(Phase.ERROR, message);
    }
}
