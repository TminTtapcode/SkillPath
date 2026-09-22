package com.skillpath.auth.application;

import java.io.Serializable;
import java.util.Set;

public record AuthenticatedAccount(long userId, String email, Set<String> roles) implements Serializable {}
