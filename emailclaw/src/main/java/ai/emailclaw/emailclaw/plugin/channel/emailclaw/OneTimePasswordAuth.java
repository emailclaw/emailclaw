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
package ai.emailclaw.emailclaw.plugin.channel.emailclaw;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;

// One-time Password Authentication
public class OneTimePasswordAuth {
    private static final String OTP_API = "https://platform.emailclaw.email/api/v1/email-user/otp";
    private static final String AT_EMAILCLAW_EMAIL = "@emailclaw.email";
    private static final Logger LOG = Logger.getLogger(OneTimePasswordAuth.class.getName());

    public static EmailAndPassword oneTimePasswordAuth(
            String registrantEmail, String oneTimePassword) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            OneTimePasswordAndName payload =
                    new OneTimePasswordAndName(registrantEmail.trim(), oneTimePassword.trim());
            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(OTP_API))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            LOG.info("response status=" + statusCode);

            if (statusCode < 200 || statusCode >= 300) {
                LOG.warning(
                        "System mode registration HTTP request failed: status="
                                + statusCode
                                + ", body="
                                + responseBody);
                return null;
            }

            if (responseBody == null || responseBody.isBlank()) {
                LOG.warning("System mode registration received empty response body");
                return null;
            }

            AuthResult authResult = mapper.readValue(responseBody, AuthResult.class);
            if (authResult.success() && authResult.refreshToken() != null) {
                String emailAddress = authResult.username() + AT_EMAILCLAW_EMAIL;

                String refreshToken = authResult.refreshToken();
                String emailPwd =
                        refreshToken.length() >= 32
                                ? refreshToken.substring(refreshToken.length() - 32)
                                : refreshToken;

                LOG.info("System mode registration validation successful, email: " + emailAddress);
                return new EmailAndPassword(emailAddress, emailPwd);
            } else {
                LOG.warning("System mode registration validation failed: " + authResult.message());
                return null;
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "System mode registration validation exception", ex);
            return null;
        }
    }

    public record EmailAndPassword(String email, String password) {}

    public record OneTimePasswordAndName(String name, String password) {}

    /**
     * Authentication result data
     */
    private record AuthResult(
            boolean success,
            String message,
            String userId,
            String username,
            String role,
            String accessToken,
            String refreshToken) {}
}
