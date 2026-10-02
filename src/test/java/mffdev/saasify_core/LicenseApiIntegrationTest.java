package mffdev.saasify_core;

import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import mffdev.saasify_core.infrastructure.security.ApiKeyAuthFilter;
import mffdev.saasify_core.infrastructure.security.WebhookSignatureFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LicenseApiIntegrationTest {

    private static final String API_KEY = "test-api-key";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LicenseRepository licenseRepository;

    @Test
    @DisplayName("POST sem API key devolve 401 e não cria licença")
    void createLicenseWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLicenseBody(30)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST com API key inválida devolve 401")
    void createLicenseWithWrongApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/licenses")
                        .header(ApiKeyAuthFilter.HEADER, "chave-errada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLicenseBody(30)))
                .andExpect(status().isUnauthorized());

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("licença criada tem o id devolvido persistido e passa a validar no GET")
    void createdLicenseIsPersistedAndValidatable() throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/licenses")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLicenseBody(30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        String licenseKey = created.get("licenseKey").asString();
        String id = created.get("id").asString();

        assertThat(licenseRepository.findByLicenseKey(licenseKey)).isPresent();
        assertThat(licenseRepository.findByLicenseKey(licenseKey).orElseThrow().getId().toString())
                .isEqualTo(id);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/licenses/{licenseKey}", licenseKey)
                        .header(ApiKeyAuthFilter.HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("POST com validade zero devolve 400 sem persistir nada")
    void createLicenseWithInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/licenses")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLicenseBody(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.validityDays").exists());

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("GET de licença inexistente devolve 404")
    void validateUnknownLicenseReturnsNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/licenses/{licenseKey}", "SAAS-UNKNOWN00")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("license_not_found"));
    }

    @Test
    @DisplayName("GET de licença expirada devolve 410")
    void validateExpiredLicenseReturnsGone() throws Exception {
        persistLicense("SAAS-EXPIRED1", LicenseEntity.LicenseStatus.ACTIVE, LocalDateTime.now().minusDays(1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/licenses/{licenseKey}", "SAAS-EXPIRED1")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("license_expired"));
    }

    @Test
    @DisplayName("GET de licença suspensa devolve 403")
    void validateSuspendedLicenseReturnsForbidden() throws Exception {
        persistLicense("SAAS-SUSPENDS", LicenseEntity.LicenseStatus.SUSPENDED, LocalDateTime.now().plusDays(30));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/licenses/{licenseKey}", "SAAS-SUSPENDS")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("license_suspended"));
    }

    @Test
    @DisplayName("webhook sem assinatura devolve 401")
    void webhookWithoutSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("SUBSCRIPTION_PAID", 30)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_missing"));

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("webhook com assinatura inválida devolve 401")
    void webhookWithInvalidSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("SUBSCRIPTION_PAID", 30))
                        .header(WebhookSignatureFilter.TIMESTAMP_HEADER, Instant.now().toString())
                        .header(WebhookSignatureFilter.SIGNATURE_HEADER, "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"));

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("webhook com timestamp antigo devolve 400 (proteção contra replay)")
    void webhookWithStaleTimestampIsRejected() throws Exception {
        String oldTimestamp = Instant.now().minusSeconds(3600).toString();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("SUBSCRIPTION_PAID", 30))
                        .header(WebhookSignatureFilter.TIMESTAMP_HEADER, oldTimestamp)
                        .header(WebhookSignatureFilter.SIGNATURE_HEADER, "sha256=" + sign(oldTimestamp, "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_timestamp_out_of_range"));

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("webhook assinado cria a licença e devolve a chave")
    void signedWebhookCreatesLicense() throws Exception {
        mockMvc.perform(signed(MockMvcRequestBuilders.post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("SUBSCRIPTION_PAID", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.licenseKey").exists());

        assertThat(licenseRepository.count()).isOne();
    }

    @Test
    @DisplayName("webhook assinado com evento não suportado devolve 400")
    void signedWebhookWithUnsupportedEventReturnsBadRequest() throws Exception {
        mockMvc.perform(signed(MockMvcRequestBuilders.post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("SUBSCRIPTION_CANCELED", 30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_webhook_event"));

        assertThat(licenseRepository.count()).isZero();
    }

    @Test
    @DisplayName("actuator health continua público")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rota não mapeada não é exposta")
    void unmappedRouteIsNotExposed() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/nao-existe")
                        .header(ApiKeyAuthFilter.HEADER, API_KEY))
                .andExpect(status().is4xxClientError());
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        RequestPostProcessor processor = servletRequest -> {
            String timestamp = Instant.now().toString();
            String body = new String(servletRequest.getContentAsByteArray(), StandardCharsets.UTF_8);

            servletRequest.addHeader(WebhookSignatureFilter.TIMESTAMP_HEADER, timestamp);
            servletRequest.addHeader(WebhookSignatureFilter.SIGNATURE_HEADER, "sha256=" + sign(timestamp, body));

            return servletRequest;
        };

        return request.with(processor);
    }

    private String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void persistLicense(String licenseKey, LicenseEntity.LicenseStatus status, LocalDateTime expiresAt) {
        LicenseEntity entity = new LicenseEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setLicenseKey(licenseKey);
        entity.setCustomerEmail("cliente@empresa.com");
        entity.setStatus(status);
        entity.setCreatedAt(expiresAt.minusDays(30));
        entity.setExpiresAt(expiresAt);
        entity.markNew();
        licenseRepository.save(entity);
    }

    private String createLicenseBody(int validityDays) {
        return """
                {
                  "tenantId": "%s",
                  "customerEmail": "cliente@empresa.com",
                  "validityDays": %d
                }
                """.formatted(UUID.randomUUID(), validityDays);
    }

    private String webhookBody(String eventType, int validityDays) {
        return """
                {
                  "eventType": "%s",
                  "tenantId": "%s",
                  "customerEmail": "cliente@empresa.com",
                  "validityDays": %d
                }
                """.formatted(eventType, UUID.randomUUID(), validityDays);
    }
}