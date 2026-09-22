package com.skillpath.shared.api;

import java.util.List;

public record ApiProblem(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String correlationId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String code) {}
}
