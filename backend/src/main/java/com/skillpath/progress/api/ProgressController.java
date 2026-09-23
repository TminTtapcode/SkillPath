package com.skillpath.progress.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.progress.application.ProgressService;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/knowledge/me")
public class ProgressController {
    private final ProgressService service;public ProgressController(ProgressService service){this.service=service;}
    @GetMapping ResponseEntity<StatePageResponse> states(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="50") int limit,@RequestParam(required=false) String cursor,@RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false)String language){var locale=SupportedLocale.resolve(language);var page=service.states(user.userId(),limit,cursor(cursor),locale);return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag()).body(new StatePageResponse(page.items().stream().map(StateResponse::from).toList(),page.hasMore(),page.nextCursor()));}
    @GetMapping("/{nodeId}") ResponseEntity<StateResponse> state(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String nodeId,@RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false)String language){var locale=SupportedLocale.resolve(language);return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag()).body(StateResponse.from(service.state(user.userId(),id(nodeId),locale)));}
    @GetMapping("/{nodeId}/evidence") EvidencePageResponse evidence(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String nodeId,@RequestParam(defaultValue="25")int limit,@RequestParam(required=false)String cursor){var page=service.evidence(user.userId(),id(nodeId),limit,cursor(cursor));return new EvidencePageResponse(page.items().stream().map(EvidenceResponse::from).toList(),page.hasMore(),page.nextCursor());}
    private long cursor(String value){return value==null?0:id(value);}private long id(String value){try{long id=Long.parseLong(value);if(id<0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR_OR_ID","Cursor or ID is invalid.");}}
    public record StatePageResponse(List<StateResponse> items,boolean hasMore,String nextCursor){}
    public record StateResponse(String knowledgeNodeId,String knowledgeNodeSlug,String knowledgeNodeName,String graphVersionId,BigDecimal recognition,BigDecimal understanding,BigDecimal recall,BigDecimal application,BigDecimal storedMastery,BigDecimal effectiveMastery,BigDecimal confidence,int evidenceCount,Instant lastEvidenceAt,Instant nextReviewAt,String status,String policyVersion){static StateResponse from(ProgressService.StateView v){var r=v.stored();return new StateResponse(Long.toString(r.nodeId()),v.nodeSlug(),v.nodeName(),Long.toString(r.graphVersionId()),r.recognition(),r.understanding(),r.recall(),r.application(),r.mastery(),v.effectiveMastery(),r.confidence(),r.evidenceCount(),r.lastEvidenceAt(),v.nextReviewAt(),v.effectiveStatus(),r.policyVersion());}}
    public record EvidencePageResponse(List<EvidenceResponse> items,boolean hasMore,String nextCursor){}
    public record EvidenceResponse(String evidenceId,String sourceType,String sourceId,String dimension,BigDecimal score,BigDecimal reliability,String policyVersion,Instant observedAt){static EvidenceResponse from(com.skillpath.progress.application.ProgressStore.EvidenceRow r){return new EvidenceResponse(Long.toString(r.id()),r.sourceType(),r.sourceId(),r.dimension(),r.score(),r.reliability(),r.policyVersion(),r.observedAt());}}
}
