package com.skillpath.knowledge.api;

import com.skillpath.knowledge.application.KnowledgeGraphService;
import com.skillpath.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/nodes")
public class KnowledgeController {

    private final KnowledgeGraphService service;

    public KnowledgeController(KnowledgeGraphService service) {
        this.service = service;
    }

    @GetMapping("/{nodeId}")
    KnowledgeGraphService.NodeView node(@PathVariable String nodeId) {
        return service.node(parseId(nodeId));
    }

    @GetMapping("/{nodeId}/prerequisites")
    List<KnowledgeGraphService.NodeView> prerequisites(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "false") boolean transitive,
            @RequestParam(defaultValue = "100") int limit) {
        return service.prerequisites(parseId(nodeId), transitive, limit, false);
    }

    @GetMapping("/{nodeId}/dependents")
    List<KnowledgeGraphService.NodeView> dependents(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "false") boolean transitive,
            @RequestParam(defaultValue = "100") int limit) {
        return service.prerequisites(parseId(nodeId), transitive, limit, true);
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KNOWLEDGE_ID", "Knowledge ID is invalid.");
        }
    }
}
