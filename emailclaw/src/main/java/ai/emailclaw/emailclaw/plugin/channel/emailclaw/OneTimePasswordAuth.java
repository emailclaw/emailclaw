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

import ai.emailclaw.emailclaw.model.ChannelInfo;
import java.util.List;
import java.util.logging.Logger;

// One-time Password Authentication
public class OneTimePasswordAuth {
    private static final String OTP_API = "https://platform.emailclaw.email/api/v1/email-user/otp";
    private static final String EMAILCLAW_EMAIL = "@emailclaw.email";
    private static final Logger LOG = Logger.getLogger(OneTimePasswordAuth.class.getName());

    public static String oneTimePasswordAuth(
            ChannelInfo channel, String registrantEmail, String oneTimePassword) {
        EmailAndPassword emailAndPassword =
                OneTimePasswordAuth.oneTimePasswordAuth(registrantEmail, oneTimePassword);
        if (emailAndPassword != null) {
            channel.setEnabled(true);
            EmailclawChannelConfig.setEmailAddress(channel, emailAndPassword.email());
            EmailclawChannelConfig.setEmailPassword(channel, emailAndPassword.password());
            EmailclawChannelConfig.setEmailAllowlistSenders(channel, List.of(registrantEmail));
            EmailclawChannelConfig.setOneTimePassword(channel, ""); // clear
            EmailclawChannelConfig.setEmailPollIntervalSeconds(channel, 30);

            LOG.info("authResult.success==true; EmailAddress==" + emailAndPassword.email());

            return emailAndPassword.email();
        } else {
            LOG.info(
                    registrantEmail
                            + " authResult.success==false; oneTimePassword=="
                            + oneTimePassword);

            return null;
        }
    }

    private static EmailAndPassword oneTimePasswordAuth(
            String registrantEmail, String oneTimePassword) {
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            OneTimePasswordAndName payload =
                    new OneTimePasswordAndName(registrantEmail.trim(), oneTimePassword.trim());
            String jsonBody = mapper.writeValueAsString(payload);

            java.net.http.HttpRequest request =
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(OTP_API))
                            .header("Content-Type", "application/json")
                            .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            LOG.info("response===" + response.body());

            AuthResult authResult = mapper.readValue(response.body(), AuthResult.class);
            if (authResult.success()) {
                String emailAddress = authResult.username() + EMAILCLAW_EMAIL;

                String emailPwd = "";
                String refreshToken = authResult.refreshToken();
                if (refreshToken != null && refreshToken.length() >= 32) {
                    emailPwd = refreshToken.substring(refreshToken.length() - 32);
                } else if (refreshToken != null) {
                    emailPwd = refreshToken;
                }

                LOG.info("System mode registration validation successful, email: " + emailAddress);
                return new EmailAndPassword(emailAddress, emailPwd);
            } else {
                LOG.warning("System mode registration validation failed: " + authResult.message());
                return null;
            }
        } catch (Exception ex) {
            LOG.log(
                    java.util.logging.Level.SEVERE,
                    "System mode registration validation exception",
                    ex);
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
