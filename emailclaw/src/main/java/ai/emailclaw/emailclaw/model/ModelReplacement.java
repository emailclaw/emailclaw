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

import java.util.Objects;

/**
 * Immutable rule representing a model replacement / upgrade.
 *
 * <p>Specifies how a deprecated or superseded model should be automatically migrated
 * to its successor model, optionally scoped to a specific provider.
 *
 * @param providerId the provider identifier this replacement applies to, or {@code null} / blank to apply across all providers
 * @param oldModelId the obsolete model identifier that should be replaced
 * @param newModelId the updated model identifier to replace with
 * @param reason human-readable justification or context for this model upgrade
 */
public record ModelReplacement(
        String providerId, String oldModelId, String newModelId, String reason) {

    /**
     * Compact constructor validating mandatory fields.
     */
    public ModelReplacement {
        Objects.requireNonNull(oldModelId, "oldModelId must not be null");
        Objects.requireNonNull(newModelId, "newModelId must not be null");
    }

    /**
     * Determines whether this replacement rule matches the given provider and model identifier.
     *
     * @param targetProviderId the provider ID to match against
     * @param targetModelId the model ID to match against
     * @return {@code true} if this replacement applies, {@code false} otherwise
     */
    public boolean matches(String targetProviderId, String targetModelId) {
        if (targetModelId == null || !targetModelId.equals(oldModelId)) {
            return false;
        }
        return providerId == null || providerId.isBlank() || providerId.equals(targetProviderId);
    }
}
