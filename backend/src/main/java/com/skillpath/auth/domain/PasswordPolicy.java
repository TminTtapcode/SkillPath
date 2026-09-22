package com.skillpath.auth.domain;

public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Password must contain between 12 and 128 characters.");
        }
    }
}
