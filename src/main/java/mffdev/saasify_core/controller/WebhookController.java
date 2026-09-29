package mffdev.saasify_core.controller;

import jakarta.validation.Valid; // <-- Importante
import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.domain.WebhookPayload;
import mffdev.saasify_core.service.LicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final LicenseService licenseService;

    public WebhookController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping("/payments")
    public ResponseEntity<String> handlePaymentWebhook(@Valid @RequestBody WebhookPayload payload) {
        // Valida se o evento é de assinatura paga
        if ("SUBSCRIPTION_PAID".equals(payload.eventType())) {
            License newLicense = licenseService.generateLicense(payload.tenantId(), payload.validityDays(), payload.customerEmail());
            return ResponseEntity.ok("Webhook processado com sucesso! Licença gerada: " + newLicense.licenseKey());
        }

        return ResponseEntity.badRequest().body("Evento de webhook ignorado ou não suportado: " + payload.eventType());
    }
}