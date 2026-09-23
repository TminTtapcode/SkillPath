package com.skillpath.review.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.review.application.ReviewService;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/reviews") public class ReviewController {
    private final ReviewService service;public ReviewController(ReviewService service){this.service=service;}
    @GetMapping("/today") ResponseEntity<PageResponse> today(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String cursor,@RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false)String language){var locale=SupportedLocale.resolve(language);var page=service.due(user.userId(),limit,cursor(cursor),locale);return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag()).body(new PageResponse(page.items().stream().map(ItemResponse::from).toList(),page.hasMore(),page.nextCursor()));}
    private long cursor(String value){if(value==null)return 0;try{return Long.parseLong(value);}catch(NumberFormatException e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REVIEW_CURSOR","Review cursor is invalid.");}}
    public record PageResponse(List<ItemResponse> items,boolean hasMore,String nextCursor){}public record ItemResponse(String reviewScheduleId,String knowledgeNodeId,String knowledgeNodeSlug,String knowledgeNodeName,int intervalIndex,Instant dueAt,String policyVersion){static ItemResponse from(ReviewService.Item i){var r=i.review();return new ItemResponse(Long.toString(r.id()),Long.toString(r.nodeId()),i.nodeSlug(),i.nodeName(),r.intervalIndex(),r.dueAt(),r.policyVersion());}}
}
