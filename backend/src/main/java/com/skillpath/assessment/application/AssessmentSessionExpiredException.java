package com.skillpath.assessment.application;

import com.skillpath.shared.api.ApiException;
import org.springframework.http.HttpStatus;

final class AssessmentSessionExpiredException extends ApiException {
    AssessmentSessionExpiredException() {
        super(HttpStatus.GONE, "ASSESSMENT_SESSION_EXPIRED", "The assessment session has expired.");
    }
}
