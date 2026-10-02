package mffdev.saasify_core.controller;

import jakarta.validation.Valid;
import mffdev.saasify_core.controller.dto.WebhookResponse;
import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.domain.WebhookPayload;
import mffdev.saasify_core.domain.exception.UnsupportedWebhookEventException;
import mffdev.saasify_core.service.LicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final String SUBSCRIPTION_PAID = "SUBSCRIPTION_PAID";

    private final LicenseService licenseService;

    public WebhookController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    /**
     * A autenticação deste endpoint é feita pelo WebhookSignatureFilter (HMAC-SHA256 do corpo),
     * então o controller cuida apenas do evento em si.
     */
    @PostMapping("/payments")
    public ResponseEntity<WebhookResponse> handlePaymentWebhook(@Valid @RequestBody WebhookPayload payload) {
        // Valida se o evento é de assinatura paga
        if (!SUBSCRIPTION_PAID.equals(payload.eventType())) {
            throw new UnsupportedWebhookEventException(payload.eventType());
        }

        License newLicense = licenseService.generateLicense(
                payload.tenantId(),
                payload.validityDays(),
                payload.customerEmail()
        );

        return ResponseEntity.ok(WebhookResponse.from(payload.eventType(), newLicense));
    }
}