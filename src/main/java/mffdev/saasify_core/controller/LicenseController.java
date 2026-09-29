package mffdev.saasify_core.controller;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import mffdev.saasify_core.service.LicenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController // Diz ao Spring que esta classe é um controlador REST (retorna JSON)
@RequestMapping("/api/v1/licenses") // URL base para os endpoints desta classe
public class LicenseController {

    private final LicenseService licenseService;
    private final LicenseRepository licenseRepository;

    public LicenseController(LicenseService licenseService, LicenseRepository licenseRepository) {
        this.licenseService = licenseService;
        this.licenseRepository = licenseRepository;
    }

    @GetMapping("/{licenseKey}")
    public ResponseEntity<?> validateLicense(@PathVariable String licenseKey) {
        var licenseOpt = licenseRepository.findByLicenseKey(licenseKey);

        if (licenseOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Licença não encontrada.");
        }

        var license = licenseOpt.get();
        return ResponseEntity.ok(license);
    }

    // Endpoint POST para gerar uma nova licença
    @PostMapping
    public ResponseEntity<License> createLicense(
            @RequestParam UUID tenantId,
            @RequestParam String customerEmail,
            @RequestParam int validityDays) {

        License newLicense = licenseService.generateLicense(tenantId, validityDays, customerEmail);

        // Retorna o status HTTP 201 (Created) com a licença gerada em JSON
        return ResponseEntity.status(HttpStatus.CREATED).body(newLicense);
    }
}