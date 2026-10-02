package mffdev.saasify_core.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateLicenseRequest(
        @NotNull(message = "O ID do tenant é obrigatório")
        UUID tenantId,

        @NotBlank(message = "O e-mail do cliente é obrigatório")
        @Email(message = "O formato do e-mail é inválido")
        String customerEmail,

        @Min(value = 1, message = "A validade mínima deve ser de pelo menos 1 dia")
        @Max(value = 3650, message = "A validade máxima é de 3650 dias")
        int validityDays
) {
}