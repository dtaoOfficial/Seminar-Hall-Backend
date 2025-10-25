package com.dtao.seminarbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Minimal Brevo (Sendinblue) API client for sending transactional emails.
 * Uses the /smtp/email endpoint.
 */
@Component
public class BrevoClient {
    private static final Logger log = LoggerFactory.getLogger(BrevoClient.class);

    private final WebClient webClient;
    private final String apiKey;

    public BrevoClient(WebClient.Builder webClientBuilder,
                       @Value("${brevo.api.key:}") String apiKey,
                       @Value("${brevo.api.base:https://api.brevo.com/v3}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Send a single HTML email via Brevo.
     *
     * @param senderName friendly name for sender (company)
     * @param from       from email (must be configured / validated in Brevo)
     * @param to         recipient list (one or more)
     * @param subject    subject line
     * @param htmlBody   html content
     * @return true if Brevo reported success (HTTP 2xx)
     */
    public boolean sendEmail(String senderName, String from, List<String> to, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Brevo API key missing — cannot send via Brevo");
            return false;
        }
        if (to == null || to.isEmpty()) {
            log.warn("No recipients provided for Brevo send");
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", senderName == null ? "" : senderName, "email", from),
                    "to", to.stream().map(email -> Map.of("email", email)).toList(),
                    "subject", subject,
                    "htmlContent", htmlBody
            );

            // call Brevo
            Mono<Map> resp = webClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class);

            Map result = resp.block();
            log.info("[BrevoClient] sendEmail result: {}", result);
            return true;
        } catch (Exception ex) {
            log.error("[BrevoClient] Failed to send email via Brevo: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
