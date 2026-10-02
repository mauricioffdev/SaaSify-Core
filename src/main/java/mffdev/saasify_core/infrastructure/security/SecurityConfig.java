package mffdev.saasify_core.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import mffdev.saasify_core.controller.dto.ErrorResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless e sem cookie de sessão: CSRF não se aplica
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Liberado aqui porque a autenticação do webhook é feita por HMAC no filtro
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Autenticação obrigatória"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpStatus.FORBIDDEN, "forbidden", "Acesso negado"))
                )
                // Precisa rodar depois do SecurityContextHolderFilter para não ter a contexto descartada
                .addFilterAfter(new ApiKeyAuthFilter(securityProperties, objectMapper),
                        SecurityContextHolderFilter.class)
                .addFilterBefore(new WebhookSignatureFilter(securityProperties, objectMapper),
                        AuthorizationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(status.value(), code, message, null));
    }
}