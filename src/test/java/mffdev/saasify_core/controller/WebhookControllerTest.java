package mffdev.saasify_core.controller;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.service.LicenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
// A assinatura HMAC é validada no teste de integração, aqui focamos no contrato do controller
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String LICENSE_KEY = "SAAS-FEED1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LicenseService licenseService;

    @Test
    @DisplayName("evento SUBSCRIPTION_PAID gera a licença e devolve 200")
    void subscriptionPaidGeneratesLicense() throws Exception {
        given(licenseService.generateLicense(eq(TENANT_ID), anyInt(), anyString()))
                .willReturn(paidLicense());

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("SUBSCRIPTION_PAID", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseKey").value(LICENSE_KEY))
                .andExpect(jsonPath("$.eventType").value("SUBSCRIPTION_PAID"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("evento não suportado devolve 400 padronizado sem emitir licença")
    void unsupportedEventReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("SUBSCRIPTION_CANCELED", 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_webhook_event"));
    }

    @Test
    @DisplayName("payload com validade zero devolve 400 antes de tocar no serviço")
    void invalidPayloadReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("SUBSCRIPTION_PAID", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.fieldErrors.validityDays").exists());
    }

    @Test
    @DisplayName("payload com e-mail inválido devolve 400")
    void invalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "SUBSCRIPTION_PAID",
                                  "tenantId": "%s",
                                  "customerEmail": "invalido",
                                  "validityDays": 30
                                }
                                """.formatted(TENANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerEmail").exists());
    }

    private String payload(String eventType, int validityDays) {
        return """
                {
                  "eventType": "%s",
                  "tenantId": "%s",
                  "customerEmail": "cliente@empresa.com",
                  "validityDays": %d
                }
                """.formatted(eventType, TENANT_ID, validityDays);
    }

    private License paidLicense() {
        LocalDateTime createdAt = LocalDateTime.now();

        return new License(UUID.randomUUID(), TENANT_ID, LICENSE_KEY, "cliente@empresa.com",
                License.LicenseStatus.ACTIVE, createdAt, createdAt.plusDays(30));
    }
}