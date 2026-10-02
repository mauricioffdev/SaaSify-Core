package mffdev.saasify_core.controller;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.domain.exception.LicenseExpiredException;
import mffdev.saasify_core.domain.exception.LicenseNotFoundException;
import mffdev.saasify_core.domain.exception.LicenseSuspendedException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LicenseController.class)
// O filtro de API key é verificado no teste de integração, aqui focamos em contrato e validação
@AutoConfigureMockMvc(addFilters = false)
class LicenseControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String LICENSE_KEY = "SAAS-ABCD1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LicenseService licenseService;

    @Test
    @DisplayName("POST cria a licença e devolve 201 com o payload gerado")
    void createLicenseReturnsCreated() throws Exception {
        given(licenseService.generateLicense(eq(TENANT_ID), anyInt(), anyString()))
                .willReturn(activeLicense());

        mockMvc.perform(post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "customerEmail": "cliente@empresa.com",
                                  "validityDays": 30
                                }
                                """.formatted(TENANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licenseKey").value(LICENSE_KEY))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
    }

    @Test
    @DisplayName("POST sem tenantId devolve 400 com o campo culpado")
    void createLicenseRejectsMissingTenant() throws Exception {
        mockMvc.perform(post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerEmail": "cliente@empresa.com",
                                  "validityDays": 30
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.fieldErrors.tenantId").exists());
    }

    @Test
    @DisplayName("POST com validade zero devolve 400")
    void createLicenseRejectsZeroValidity() throws Exception {
        mockMvc.perform(post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "customerEmail": "cliente@empresa.com",
                                  "validityDays": 0
                                }
                                """.formatted(TENANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.validityDays").exists());
    }

    @Test
    @DisplayName("POST com e-mail inválido devolve 400")
    void createLicenseRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "customerEmail": "nao-e-email",
                                  "validityDays": 30
                                }
                                """.formatted(TENANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerEmail").exists());
    }

    @Test
    @DisplayName("POST com corpo malformado devolve 400 padronizado")
    void createLicenseRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    @DisplayName("GET devolve 200 para licença ativa")
    void validateLicenseReturnsOk() throws Exception {
        given(licenseService.validateLicense(LICENSE_KEY)).willReturn(activeLicense());

        mockMvc.perform(get("/api/v1/licenses/{licenseKey}", LICENSE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseKey").value(LICENSE_KEY))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET devolve 404 quando a licença não existe")
    void validateLicenseReturnsNotFound() throws Exception {
        given(licenseService.validateLicense("SAAS-UNKNOWN00"))
                .willThrow(new LicenseNotFoundException("SAAS-UNKNOWN00"));

        mockMvc.perform(get("/api/v1/licenses/{licenseKey}", "SAAS-UNKNOWN00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("license_not_found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET devolve 410 para licença expirada")
    void validateLicenseReturnsGone() throws Exception {
        given(licenseService.validateLicense("SAAS-EXPIRED1"))
                .willThrow(new LicenseExpiredException("SAAS-EXPIRED1", LocalDateTime.now().minusDays(1)));

        mockMvc.perform(get("/api/v1/licenses/{licenseKey}", "SAAS-EXPIRED1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("license_expired"));
    }

    @Test
    @DisplayName("GET devolve 403 para licença suspensa")
    void validateLicenseReturnsForbidden() throws Exception {
        given(licenseService.validateLicense("SAAS-SUSPENDS"))
                .willThrow(new LicenseSuspendedException("SAAS-SUSPENDS"));

        mockMvc.perform(get("/api/v1/licenses/{licenseKey}", "SAAS-SUSPENDS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("license_suspended"));
    }

    private License activeLicense() {
        LocalDateTime createdAt = LocalDateTime.now();

        return new License(UUID.randomUUID(), TENANT_ID, LICENSE_KEY, "cliente@empresa.com",
                License.LicenseStatus.ACTIVE, createdAt, createdAt.plusDays(30));
    }
}