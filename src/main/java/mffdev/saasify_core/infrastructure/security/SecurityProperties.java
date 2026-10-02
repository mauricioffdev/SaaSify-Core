package mffdev.saasify_core.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "saasify.security")
public record SecurityProperties(
        List<String> apiKeys,
        String webhookSecret,
        Duration webhookTolerance
) {
    public SecurityProperties {
        apiKeys = apiKeys == null
                ? List.of()
                : apiKeys.stream().filter(key -> key != null && !key.isBlank()).map(String::trim).toList();
        webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        webhookTolerance = webhookTolerance == null ? Duration.ofMinutes(5) : webhookTolerance;
    }

    public boolean hasApiKeys() {
        return !apiKeys.isEmpty();
    }

    public boolean hasWebhookSecret() {
        return !webhookSecret.isEmpty();
    }
}