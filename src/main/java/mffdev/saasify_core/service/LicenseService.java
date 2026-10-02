package mffdev.saasify_core.service;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.domain.exception.LicenseExpiredException;
import mffdev.saasify_core.domain.exception.LicenseKeyGenerationException;
import mffdev.saasify_core.domain.exception.LicenseNotFoundException;
import mffdev.saasify_core.domain.exception.LicenseSuspendedException;
import mffdev.saasify_core.infrastructure.persistence.LicenseMapper;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final int MAX_KEY_GENERATION_ATTEMPTS = 5;

    private final LicenseRepository licenseRepository;
    private final LicenseMapper licenseMapper;

    public LicenseService(LicenseRepository licenseRepository, LicenseMapper licenseMapper) {
        this.licenseRepository = licenseRepository;
        this.licenseMapper = licenseMapper;
    }

    public License generateLicense(UUID tenantId, int validityDays, String customerEmail) {
        String licenseKey = generateUniqueLicenseKey();
        License license = License.createNew(tenantId, licenseKey, validityDays, customerEmail);

        licenseRepository.save(licenseMapper.toEntity(license));

        log.info("Licença emitida: key={} tenant={} expiresAt={}", license.licenseKey(), license.tenantId(),
                license.expiresAt());

        return license;
    }

    /**
     * Regra de negócio da consulta: a licença precisa existir, estar ativa e não vencida.
     * Qualquer violação vira exceção de domínio traduzida em status HTTP pelo handler.
     */
    public License validateLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new LicenseNotFoundException(String.valueOf(licenseKey));
        }

        License license = licenseRepository.findByLicenseKey(licenseKey.trim())
                .map(licenseMapper::toDomain)
                .orElseThrow(() -> new LicenseNotFoundException(licenseKey));

        LocalDateTime now = LocalDateTime.now();

        if (license.isSuspended()) {
            throw new LicenseSuspendedException(license.licenseKey());
        }
        if (license.status() == License.LicenseStatus.EXPIRED || license.isExpired(now)) {
            throw new LicenseExpiredException(license.licenseKey(), license.expiresAt());
        }

        log.info("Licença validada: key={} tenant={}", license.licenseKey(), license.tenantId());

        return license;
    }

    private String generateUniqueLicenseKey() {
        for (int attempt = 0; attempt < MAX_KEY_GENERATION_ATTEMPTS; attempt++) {
            String candidate = License.generateLicenseKey();
            if (!licenseRepository.existsByLicenseKey(candidate)) {
                return candidate;
            }
        }

        throw new LicenseKeyGenerationException();
    }
}