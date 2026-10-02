package mffdev.saasify_core.infrastructure.persistence;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "licenses")
public class LicenseEntity implements Persistable<UUID> {

    // O id é gerado pelo domínio (License.createNew), por isso não usamos @GeneratedValue.
    // Persistable evita que o Spring Data trate a entidade como detached e dispare merge.
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String licenseKey;

    @Column(nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Transient
    private boolean newEntity;

    public enum LicenseStatus {
        ACTIVE, EXPIRED, SUSPENDED
    }

    // Getters e Setters tradicionais da entidade de banco
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    // Marca a entidade como ainda não persistida (id atribuído pela aplicação)
    public void markNew() {
        this.newEntity = true;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}