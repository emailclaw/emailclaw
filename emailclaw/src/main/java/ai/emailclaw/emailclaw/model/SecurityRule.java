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
package ai.emailclaw.emailclaw.model;

/**
 * Security detection rule object.
 */
public record SecurityRule(
        String ruleId,
        String category,
        String description,
        String severity,
        boolean builtIn,
        boolean enabled,
        boolean autoDeny,
        boolean viewDetails) {
    public SecurityRule {
        if (description == null) description = "";
        if (severity == null) severity = "HIGH";
    }

    public SecurityRule(
            String ruleId,
            String category,
            String severity,
            String description,
            boolean builtIn,
            boolean enabled) {
        this(ruleId, category, description, severity, builtIn, enabled, false, false);
    }

    public SecurityRule(
            String ruleId,
            String category,
            String severity,
            String description,
            boolean builtIn,
            boolean enabled,
            boolean autoDeny) {
        this(ruleId, category, description, severity, builtIn, enabled, autoDeny, false);
    }

    public SecurityRule withEnabled(boolean enabled) {
        return new SecurityRule(
                ruleId, category, description, severity, builtIn, enabled, autoDeny, viewDetails);
    }

    public SecurityRule withAutoDeny(boolean autoDeny) {
        return new SecurityRule(
                ruleId, category, description, severity, builtIn, enabled, autoDeny, viewDetails);
    }
}
