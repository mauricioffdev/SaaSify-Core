package mffdev.saasify_core.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import mffdev.saasify_core.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Os filtros de segurança rodam fora do DispatcherServlet, então não passam pelo
 * GlobalExceptionHandler. Aqui centralizamos a escrita da resposta de erro.
 */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                      String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(status.value(), code, message, null));
    }
}