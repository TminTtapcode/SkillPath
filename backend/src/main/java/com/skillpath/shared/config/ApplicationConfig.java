package com.skillpath.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI skillPathOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SkillPath API")
                .version("v1")
                .description("Phase 1 authentication, profile, and goal contracts."));
    }
}
