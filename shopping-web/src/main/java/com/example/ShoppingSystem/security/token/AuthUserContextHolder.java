package com.example.ShoppingSystem.security.token;

public final class AuthUserContextHolder {

    private static final ThreadLocal<AuthUserContext> HOLDER = new ThreadLocal<>();

    private AuthUserContextHolder() {
    }

    public static void set(AuthUserContext context) {
        HOLDER.set(context);
    }

    public static AuthUserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
