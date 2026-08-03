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
package ai.emailclaw.emailclaw;

import ai.emailclaw.emailclaw.model.AgentIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.StreamCallback;
import ai.emailclaw.emailclaw.storage.AppContext;
import io.agentscope.core.message.Msg;

public class ManualSmoke {

    public static void main(String[] args) {
        ApplicationBootstrap.BootstrapResult boot = ApplicationBootstrap.initialize();
        AppContext repository = boot.repository();
        ProviderService providerService = boot.providerService();
        AgentService agentService = boot.agentService();
        ChatService chatService = boot.chatService();
        System.out.println("providers=" + providerService.listProviders().size());
        ProviderInfo opencode =
                providerService.listProviders().stream()
                        .filter(p -> "opencode".equals(p.getId()))
                        .findFirst()
                        .orElseThrow();
        System.out.println("opencode.status=" + providerService.status(opencode));
        System.out.println(
                "opencode.models=" + opencode.allModels().stream().map(m -> m.getName()).toList());
        AgentInfo agent =
                agentService.findById(AgentIds.DEFAULT).orElse(agentService.currentDefault());
        ChatSessionInfo session = chatService.newSession(agent.getId());
        chatService.sendMessage(
                agent,
                opencode,
                "big-pickle",
                session,
                "Please return a test message",
                new StreamCallback() {

                    @Override
                    public void onPart(ChatMessagePart part, boolean startsNew) {}

                    @Override
                    public void onCompleted(Msg message) {
                        System.out.println("chat.result=" + message.getTextContent());
                    }
                });
    }
}
