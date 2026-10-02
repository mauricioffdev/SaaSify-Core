package mffdev.saasify_core.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Protege as rotas administrativas (/api/v1/**) com uma API Key estática.
 * Webhooks ficam de fora: quem valida o webhook é o WebhookSignatureFilter (HMAC).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    public static final String PROTECTED_PREFIX = "/api/v1/";
    public static final String PUBLIC_PREFIX = "/api/v1/webhooks/";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String PRINCIPAL = "saasify-admin";

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER);
        if (!isValid(providedKey)) {
            log.warn("Acesso não autorizado em {} (API key ausente ou inválida)", request.getRequestURI());
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "unauthorized", "Credencial de API key ausente ou inválida");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(PROTECTED_PREFIX) && !path.startsWith(PUBLIC_PREFIX);
    }

    private boolean isValid(String providedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }

        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        for (String configured : securityProperties.apiKeys()) {
            byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }

        return false;
    }
}