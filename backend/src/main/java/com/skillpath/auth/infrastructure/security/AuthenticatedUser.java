package com.skillpath.auth.infrastructure.security;

import com.skillpath.auth.application.AuthenticatedAccount;
import java.io.Serial;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final Collection<SimpleGrantedAuthority> authorities;

    public AuthenticatedUser(AuthenticatedAccount account) {
        this.userId = account.userId();
        this.email = account.email();
        this.authorities = account.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    public long userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return Long.toString(userId);
    }
}
