package com.skillpath.planner.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.planner.application.PlannerService;
import com.skillpath.planner.application.AdaptiveReplanService;
import com.skillpath.shared.localization.SupportedLocale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlannerController {
    private final PlannerService service;
    private final AdaptiveReplanService adaptive;
    public PlannerController(PlannerService service,AdaptiveReplanService adaptive){
        this.service=service;this.adaptive=adaptive;
    }

    public record AvailableMinutesRequest(int availableMinutes) {}

    @PutMapping("/learning/today/available-minutes")
    ResponseEntity<AdaptiveReplanService.OverrideView> availableMinutes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody AvailableMinutesRequest body){
        return ResponseEntity.ok(adaptive.override(user.userId(),key,body.availableMinutes()));
    }

    @PostMapping("/learning/today/refresh")
    ResponseEntity<PlannerService.TodayView> refresh(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        var locale=SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag())
                .body(adaptive.refresh(user.userId(),key,locale));
    }

    @GetMapping("/learning/today/replan-status")
    ResponseEntity<AdaptiveReplanService.ReplanStatusView> replanStatus(
            @AuthenticationPrincipal AuthenticatedUser user){
        return ResponseEntity.ok(adaptive.status(user.userId()));
    }

    @GetMapping("/learning/today")
    ResponseEntity<PlannerService.TodayView> today(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        var locale=SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag())
                .body(service.today(user.userId(),locale));
    }

    @GetMapping("/learning/today/plans/{id}")
    ResponseEntity<PlannerService.TodayView> historical(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        long planId;
        try{planId=Long.parseLong(id);if(planId<1)throw new NumberFormatException();}
        catch(NumberFormatException exception){return ResponseEntity.notFound().build();}
        var locale=SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag())
                .body(service.historical(user.userId(),planId,locale));
    }

    @PostMapping("/learning/today/generate")
    ResponseEntity<PlannerService.TodayView> generate(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        var locale=SupportedLocale.resolve(language);
        var result=service.generate(user.userId(),key,locale);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
                .header(HttpHeaders.CONTENT_LANGUAGE,locale.tag()).body(result.plan());
    }

    @PostMapping("/learning/today/revise")
    ResponseEntity<PlannerService.TodayView> revise(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        var locale=SupportedLocale.resolve(language);
        var result=service.revise(user.userId(),key,locale);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
                .header(HttpHeaders.CONTENT_LANGUAGE,locale.tag()).body(result.plan());
    }

    @GetMapping("/roadmap")
    ResponseEntity<PlannerService.RoadmapView> roadmap(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="50") int limit,
            @RequestParam(required=false) String cursor,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String language){
        var locale=SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag())
                .body(service.roadmap(user.userId(),limit,cursor,locale));
    }
}
