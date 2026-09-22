package com.skillpath.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.shared.api.ApiProblem;
import com.skillpath.shared.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ApiProblem problem = new ApiProblem(
                "https://skillpath.app/problems/" + code.toLowerCase().replace('_', '-'),
                status.getReasonPhrase(),
                status.value(),
                code,
                detail,
                request.getRequestURI(),
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
                List.of());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
