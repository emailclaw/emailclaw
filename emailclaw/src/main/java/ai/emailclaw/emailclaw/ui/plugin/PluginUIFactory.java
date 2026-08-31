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

import ai.emailclaw.emailclaw.plugin.channel.emailclaw.ui.EmailclawChannelConfigViewProvider;
import java.util.Map;
import java.util.Optional;

/**
 * Plugin UI view provider factory.
 * Responsible for discovering and assembling registered front-end view providers, implementing dependency inversion at the UI layer.
 */
public class PluginUIFactory {

    private static final Map<String, CustomConfigViewProvider> PROVIDERS =
            Map.of(
                    //                    ai.emailclaw.emailclaw.channel.ChannelIds.DINGTALK,
                    //                            new DingTalkConfigViewProvider(),
                    ai.emailclaw.emailclaw.channel.ChannelIds.EMAILCLAW,
                    new EmailclawChannelConfigViewProvider());

    /**
     * Get the corresponding custom view provider based on the plugin ID.
     *
     * @param pluginId Plugin ID
     * @return Matching view provider, returns empty if none
     */
    public static Optional<CustomConfigViewProvider> getProvider(String pluginId) {
        return Optional.ofNullable(PROVIDERS.get(pluginId));
    }
}
