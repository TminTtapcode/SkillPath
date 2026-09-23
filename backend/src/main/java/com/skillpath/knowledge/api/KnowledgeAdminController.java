package com.skillpath.knowledge.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.knowledge.application.KnowledgeGraphService;
import com.skillpath.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/knowledge/versions")
public class KnowledgeAdminController {

    private final KnowledgeGraphService service;

    public KnowledgeAdminController(KnowledgeGraphService service) {
        this.service = service;
    }

    @PostMapping("/{versionId}/validate")
    KnowledgeGraphService.ValidationView validate(
            @PathVariable String versionId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return service.validate(parseId(versionId), principal.userId(), correlationId);
    }

    @PostMapping("/{versionId}/publish")
    KnowledgeGraphService.PublicationView publish(
            @PathVariable String versionId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return service.publish(parseId(versionId), principal.userId(), correlationId);
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_VERSION_ID", "Graph version ID is invalid.");
        }
    }
}
