package mffdev.saasify_core.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/**
 * Autentica o webhook do gateway de pagamento exigindo HMAC-SHA256 do corpo
 * (no formato timestamp.corpo) e rejeitando timestamps fora da janela de replay.
 */
public class WebhookSignatureFilter extends OncePerRequestFilter {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    public static final String WEBHOOK_PREFIX = "/api/v1/webhooks/";

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureFilter.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookSignatureFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this(securityProperties, objectMapper, Clock.systemUTC());
    }

    WebhookSignatureFilter(SecurityProperties securityProperties, ObjectMapper objectMapper, Clock clock) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!securityProperties.hasWebhookSecret()) {
            log.error("Webhook recebido sem SAASIFY_WEBHOOK_SECRET configurado");
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                    "webhook_auth_not_configured", "Autenticação de webhook não configurada");
            return;
        }

        String timestampHeader = request.getHeader(TIMESTAMP_HEADER);
        String signatureHeader = request.getHeader(SIGNATURE_HEADER);

        if (timestampHeader == null || signatureHeader == null) {
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "webhook_signature_missing", "Headers de assinatura do webhook são obrigatórios");
            return;
        }

        Duration tolerance = securityProperties.webhookTolerance();
        if (isOutsideToleranceWindow(timestampHeader, tolerance)) {
            log.warn("Webhook rejeitado por timestamp fora da janela de {}s", tolerance.toSeconds());
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.BAD_REQUEST,
                    "webhook_timestamp_out_of_range",
                    "O timestamp do webhook está fora da janela aceita");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, MAX_BODY_BYTES);
        if (cachedRequest.exceeds(MAX_BODY_BYTES)) {
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.PAYLOAD_TOO_LARGE,
                    "webhook_payload_too_large", "O corpo do webhook excede o tamanho máximo permitido");
            return;
        }

        if (!hasValidSignature(cachedRequest.body(), timestampHeader, signatureHeader)) {
            log.warn("Webhook rejeitado por assinatura inválida em {}", request.getRequestURI());
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "webhook_signature_invalid", "Assinatura do webhook inválida");
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isOutsideToleranceWindow(String timestampHeader, Duration tolerance) {
        Instant sentAt;
        try {
            sentAt = Instant.parse(timestampHeader);
        } catch (DateTimeParseException ex) {
            return true;
        }

        return Duration.between(sentAt, clock.instant()).abs().compareTo(tolerance) > 0;
    }

    private boolean hasValidSignature(byte[] body, String timestamp, String signatureHeader) {
        byte[] provided = signatureHeader.trim().startsWith(SIGNATURE_PREFIX)
                ? signatureHeader.trim().substring(SIGNATURE_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8)
                : signatureHeader.trim().getBytes(StandardCharsets.UTF_8);

        byte[] expected = HexFormat.of()
                .formatHex(sign(timestamp + "." + new String(body, StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, provided);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(securityProperties.webhookSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível calcular a assinatura HMAC do webhook", ex);
        }
    }
}