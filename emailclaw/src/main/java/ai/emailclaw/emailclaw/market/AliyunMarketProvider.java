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

/**
 * Aliyun AgentExplorer skill market Provider.
 *
 * <p>Emailclaw accesses via Aliyun SDK (ACS3 signature); Emailclaw desktop version currently does not build in this SDK,
 * therefore it is marked as unavailable when AK/SK are not configured to avoid misleading users.
 */
public class AliyunMarketProvider implements MarketProvider {
    private static final String[] CRED_ENV_KEYS = {
        "ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"
    };

    @Override
    public String key() {
        return "aliyun";
    }

    @Override
    public String label() {
        return "Aliyun";
    }

    @Override
    public Availability availability() {
        for (String envKey : CRED_ENV_KEYS) {
            String val = System.getenv(envKey);
            if (val == null || val.isBlank()) {
                return new Availability(
                        false,
                        "Missing environment variable "
                                + envKey
                                + " (Aliyun AK/SK must be configured to search the Aliyun market)");
            }
        }
        // Desktop version has not yet integrated alibabacloud-tea-openapi, keep unavailable prompt
        // before aligning capabilities with Python version.
        return new Availability(
                false,
                "Emailclaw has not integrated Aliyun AgentExplorer SDK yet, please use ClawHub or"
                        + " ModelScope");
    }

    @Override
    public SearchOutcome search(String query, int limit, int page, String lang) {
        throw new UnsupportedOperationException(
                "Aliyun market provider is not available in Emailclaw desktop yet");
    }
}
