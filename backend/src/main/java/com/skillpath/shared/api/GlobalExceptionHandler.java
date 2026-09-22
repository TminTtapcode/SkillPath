package com.skillpath.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiProblem> handleApi(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiProblem.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiProblem.FieldError(
                        error.getField(), error.getDefaultMessage() == null ? "INVALID" : error.getDefaultMessage()))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "One or more fields are invalid.",
                request,
                errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiProblem> handleMissingHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_HEADER",
                exception.getHeaderName() + " is required.",
                request,
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiProblem> handleIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "STATE_CONFLICT",
                "The requested state conflicts with existing data.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected request failure: method={}, path={}, correlationId={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
                exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                request,
                List.of());
    }

    private ResponseEntity<ApiProblem> response(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            List<ApiProblem.FieldError> fieldErrors) {
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        ApiProblem body = new ApiProblem(
                "https://skillpath.app/problems/" + code.toLowerCase().replace('_', '-'),
                status.getReasonPhrase(),
                status.value(),
                code,
                detail,
                request.getRequestURI(),
                correlationId,
                fieldErrors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body);
    }
}
