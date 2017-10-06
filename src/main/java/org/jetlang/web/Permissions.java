package org.jetlang.web;

public interface Permissions<T extends AuthenticatedUser> {

    default boolean isAthenticated(HttpRequest headers, T sessionState) {
        return sessionState.isAuthenticated();
    }

    boolean isAuthorized(HttpRequest headers, T sessionState);

    boolean authenticate(HttpRequest headers, String username, String password, T sessionState);

    default void setAthenticated(T sessionState, String username) {
        sessionState.setAuthenticated(username);
    }
}
