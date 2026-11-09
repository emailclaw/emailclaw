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
package ai.emailclaw.emailclaw.cli;

import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.ProviderService;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * Tab-completer implementation for Emailclaw CLI commands and subcommands.
 */
public class CliCompleter implements Completer {

    private static final String GROUP_COMMANDS = "Slash Commands";
    private static final String GROUP_SUBCOMMANDS = "Subcommands";
    private static final String GROUP_MODELS = "Models";

    private final ProviderService providerService;

    public CliCompleter() {
        this(null);
    }

    public CliCompleter(ProviderService providerService) {
        this.providerService = providerService;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        if (buffer == null) {
            return;
        }

        String trimmed = buffer.trim();
        List<String> words = line.words();
        int wordIndex = line.wordIndex();

        // If typing the primary command
        if (wordIndex == 0 || (words.size() <= 1 && !buffer.endsWith(" "))) {
            addCommandCandidates(candidates);
            return;
        }

        // Subcommand completions
        String primaryCmd = words.get(0).toLowerCase();
        if ("/session".equals(primaryCmd) && wordIndex == 1) {
            candidates.add(
                    new Candidate(
                            "list",
                            "list",
                            GROUP_SUBCOMMANDS,
                            "List all sessions for current agent",
                            null,
                            null,
                            true));
            candidates.add(
                    new Candidate(
                            "new",
                            "new",
                            GROUP_SUBCOMMANDS,
                            "Create and switch to a new session",
                            null,
                            null,
                            true));
            candidates.add(
                    new Candidate(
                            "switch",
                            "switch",
                            GROUP_SUBCOMMANDS,
                            "Switch active session by ID",
                            null,
                            null,
                            true));
        } else if ("/agent".equals(primaryCmd) && wordIndex == 1) {
            candidates.add(
                    new Candidate(
                            "list",
                            "list",
                            GROUP_SUBCOMMANDS,
                            "List all configured agents",
                            null,
                            null,
                            true));
            candidates.add(
                    new Candidate(
                            "switch",
                            "switch",
                            GROUP_SUBCOMMANDS,
                            "Switch default agent by ID",
                            null,
                            null,
                            true));
        } else if ("/model".equals(primaryCmd) && wordIndex == 1) {
            candidates.add(
                    new Candidate(
                            "list",
                            "list",
                            GROUP_SUBCOMMANDS,
                            "List supported models for provider",
                            null,
                            null,
                            true));
            candidates.add(
                    new Candidate(
                            "switch",
                            "switch",
                            GROUP_SUBCOMMANDS,
                            "Switch model for current agent",
                            null,
                            null,
                            true));
        } else if ("/model".equals(primaryCmd)
                && wordIndex == 2
                && words.size() > 1
                && "switch".equalsIgnoreCase(words.get(1))) {
            if (providerService != null) {
                for (ProviderInfo p : providerService.listProviders()) {
                    String pId = p.getId();
                    String pName = p.getName();
                    for (ModelInfo m : p.allModels()) {
                        String mId = m.getId();
                        candidates.add(
                                new Candidate(
                                        pId + "/" + mId,
                                        pId + "/" + mId,
                                        GROUP_MODELS,
                                        m.getName() + " (" + pName + ")",
                                        null,
                                        null,
                                        true));
                        candidates.add(
                                new Candidate(
                                        mId,
                                        mId,
                                        GROUP_MODELS,
                                        m.getName() + " (" + pName + ")",
                                        null,
                                        null,
                                        true));
                    }
                }
            }
        }
    }

    private void addCommandCandidates(List<Candidate> candidates) {
        candidates.add(
                new Candidate(
                        "/help",
                        "/help",
                        GROUP_COMMANDS,
                        "Show commands and general usage",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/session",
                        "/session",
                        GROUP_COMMANDS,
                        "Manage sessions (list, new, switch)",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/agent",
                        "/agent",
                        GROUP_COMMANDS,
                        "List and switch active agents",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/model",
                        "/model",
                        GROUP_COMMANDS,
                        "List and switch models for agent",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/skills",
                        "/skills",
                        GROUP_COMMANDS,
                        "Inspect loaded workspace and pool skills",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/plan",
                        "/plan",
                        GROUP_COMMANDS,
                        "View current agent plan and execution steps",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/history",
                        "/history",
                        GROUP_COMMANDS,
                        "Display recent conversation history",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/compact",
                        "/compact",
                        GROUP_COMMANDS,
                        "Trigger session history compaction",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/clear",
                        "/clear",
                        GROUP_COMMANDS,
                        "Clear terminal screen",
                        null,
                        null,
                        true));
        candidates.add(
                new Candidate(
                        "/exit", "/exit", GROUP_COMMANDS, "Exit Emailclaw CLI", null, null, true));
        candidates.add(
                new Candidate(
                        "/quit", "/quit", GROUP_COMMANDS, "Quit Emailclaw CLI", null, null, true));
    }
}
