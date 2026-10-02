package mffdev.saasify_core.controller.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import mffdev.saasify_core.controller.dto.ErrorResponse;
import mffdev.saasify_core.domain.exception.BusinessException;
import mffdev.saasify_core.domain.exception.InvalidLicenseDataException;
import mffdev.saasify_core.domain.exception.LicenseExpiredException;
import mffdev.saasify_core.domain.exception.LicenseKeyGenerationException;
import mffdev.saasify_core.domain.exception.LicenseNotFoundException;
import mffdev.saasify_core.domain.exception.LicenseSuspendedException;
import mffdev.saasify_core.domain.exception.UnsupportedWebhookEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex);
        if (status.is5xxServerError()) {
            log.error("Falha de negócio {}: {}", ex.code(), ex.getMessage(), ex);
        }

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), ex.code(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "validation_error",
                        "Um ou mais campos são inválidos", request.getRequestURI(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                  HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> errors.putIfAbsent(violation.getPropertyPath().toString(),
                        violation.getMessage()));

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "validation_error",
                        "Um ou mais campos são inválidos", request.getRequestURI(), errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "malformed_request",
                        "A requisição enviada é inválida ou está incompleta", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "resource_not_found",
                        "Recurso não encontrado", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "internal_error",
                        "Ocorreu um erro inesperado", request.getRequestURI()));
    }

    private HttpStatus statusFor(BusinessException ex) {
        return switch (ex) {
            case LicenseNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case LicenseExpiredException ignored -> HttpStatus.GONE;
            case LicenseSuspendedException ignored -> HttpStatus.FORBIDDEN;
            case InvalidLicenseDataException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnsupportedWebhookEventException ignored -> HttpStatus.BAD_REQUEST;
            case LicenseKeyGenerationException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}