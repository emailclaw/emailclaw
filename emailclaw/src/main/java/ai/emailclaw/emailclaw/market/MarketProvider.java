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
package ai.emailclaw.emailclaw.market;

import ai.emailclaw.emailclaw.model.MarketProviderInfo;
import ai.emailclaw.emailclaw.model.MarketResult;
import java.util.List;

/**
 * Skill market remote platform adapter interface.
 *
 * <p>Each implementation corresponds to a Provider under Emailclaw {@code market/providers}.
 */
public interface MarketProvider {

    /** Platform key, keep consistent with Emailclaw. */
    String key();

    /** Platform display name. */
    String label();

    /** Returns availability and unavailable reason. */
    Availability availability();

    /**
     * Search skills.
     *
     * @param query User input keyword
     * @param limit Max items per page
     * @param page Page number (starts from 1)
     * @param lang UI language (used for ModelScope multi-language fields)
     * @return List of results, whether there are more, total number (-1 if unknown)
     */
    SearchOutcome search(String query, int limit, int page, String lang) throws Exception;

    /** Availability check result. */
    record Availability(boolean available, String reason) {}

    /** Single search return value. */
    record SearchOutcome(List<MarketResult> results, boolean hasMore, int total) {}

    /** Convert to meta info object used by API layer. */
    default MarketProviderInfo toInfo() {
        Availability a = availability();
        MarketProviderInfo info = new MarketProviderInfo();
        info.setKey(key());
        info.setLabel(label());
        info.setAvailable(a.available());
        info.setReason(a.reason() == null ? "" : a.reason());
        return info;
    }
}
