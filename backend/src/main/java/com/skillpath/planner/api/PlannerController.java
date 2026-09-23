package com.skillpath.planner.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.planner.application.PlannerService;
import com.skillpath.shared.localization.SupportedLocale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlannerController {
    private final PlannerService service;
    public PlannerController(PlannerService service){this.service=service;}

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
