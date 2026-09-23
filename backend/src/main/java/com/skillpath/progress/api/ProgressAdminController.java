package com.skillpath.progress.api;

import com.skillpath.progress.application.ProgressService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin/progress")
public class ProgressAdminController {
    private final ProgressService service;public ProgressAdminController(ProgressService service){this.service=service;}
    @PostMapping("/rebuild") public RebuildResponse rebuild(){return new RebuildResponse("COMPLETED",service.rebuild());}
    public record RebuildResponse(String status,int projectedNodes){}
}
