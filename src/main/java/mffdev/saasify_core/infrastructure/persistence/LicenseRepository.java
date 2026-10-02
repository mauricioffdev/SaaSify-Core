package mffdev.saasify_core.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository // Opcional, mas boa prática para indicar que é um componente de acesso a dados
public interface LicenseRepository extends JpaRepository<LicenseEntity, UUID> {

    // Metodo customizado para buscar uma licença pela ‘string’ da chave (ex: "SAAS-1234ABCD")
    Optional<LicenseEntity> findByLicenseKey(String licenseKey);

    // Usado pelo service para não gerar uma chave que já exista (coluna é unique)
    boolean existsByLicenseKey(String licenseKey);
}