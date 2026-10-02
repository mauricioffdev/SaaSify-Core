package mffdev.saasify_core.controller;

import mffdev.saasify_core.controller.dto.CreateLicenseRequest;
import mffdev.saasify_core.controller.dto.LicenseResponse;
import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.service.LicenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // Diz ao Spring que esta classe é um controlador REST (retorna JSON)
@RequestMapping("/api/v1/licenses") // URL base para os endpoints desta classe
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping("/{licenseKey}")
    public ResponseEntity<LicenseResponse> validateLicense(@PathVariable String licenseKey) {
        License license = licenseService.validateLicense(licenseKey);

        return ResponseEntity.ok(LicenseResponse.from(license));
    }

    // Endpoint POST para gerar uma nova licença
    @PostMapping
    public ResponseEntity<LicenseResponse> createLicense(@Valid @RequestBody CreateLicenseRequest request) {
        License newLicense = licenseService.generateLicense(
                request.tenantId(),
                request.validityDays(),
                request.customerEmail()
        );

        // Retorna o status HTTP 201 (Created) com a licença gerada em JSON
        return ResponseEntity.status(HttpStatus.CREATED).body(LicenseResponse.from(newLicense));
    }
}