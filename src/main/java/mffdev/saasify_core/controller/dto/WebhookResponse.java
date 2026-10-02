package mffdev.saasify_core.controller.dto;

import mffdev.saasify_core.domain.License;

import java.time.LocalDateTime;

public record WebhookResponse(
        String eventType,
        String licenseKey,
        LocalDateTime expiresAt,
        License.LicenseStatus status
) {
    public static WebhookResponse from(String eventType, License license) {
        return new WebhookResponse(
                eventType,
                license.licenseKey(),
                license.expiresAt(),
                license.status()
        );
    }
}