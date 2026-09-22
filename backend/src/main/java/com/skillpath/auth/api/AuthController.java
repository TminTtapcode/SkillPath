package com.skillpath.auth.api;

import com.skillpath.auth.application.AuthService;
import com.skillpath.auth.application.AuthenticatedAccount;
import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.user.application.UserProfiles;
import com.skillpath.user.domain.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final UserProfiles userProfiles;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;

    public AuthController(
            AuthService authService,
            UserProfiles userProfiles,
            SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contextRepository) {
        this.authService = authService;
        this.userProfiles = userProfiles;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/auth/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/auth/register")
    ResponseEntity<ProfileResponse> register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthService.RegisteredAccount registered = authService.register(
                body.email(), body.password(), body.displayName(), body.timezone());
        establishSession(registered.account(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(registered.account(), registered.profile()));
    }

    @PostMapping("/auth/login")
    ProfileResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthenticatedAccount account = authService.login(body.email(), body.password());
        establishSession(account, request, response);
        return ProfileResponse.from(account, userProfiles.require(account.userId()));
    }

    @GetMapping("/me")
    ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedAccount account = authService.requireAccount(principal.userId());
        return ProfileResponse.from(account, userProfiles.require(principal.userId()));
    }

    private void establishSession(
            AuthenticatedAccount account,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthenticatedUser principal = new AuthenticatedUser(account);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 64) String timezone) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record CsrfResponse(String headerName, String parameterName, String token) {}

    public record ProfileResponse(
            String id, String email, String displayName, String timezone, long version) {
        static ProfileResponse from(AuthenticatedAccount account, UserProfile profile) {
            return new ProfileResponse(
                    Long.toString(profile.id()),
                    account.email(),
                    profile.displayName(),
                    profile.timezone(),
                    profile.version());
        }
    }
}
