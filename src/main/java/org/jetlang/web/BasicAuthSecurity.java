package org.jetlang.web;

import org.jetlang.fibers.Fiber;

import java.nio.charset.Charset;
import java.util.Base64;

public class BasicAuthSecurity<T extends AuthenticatedUser> implements HttpSecurity<T> {

    private final String name;
    private final Permissions<T> auth;
    private final AuthFailuresHandler<T> failures;

    public BasicAuthSecurity(String name, Permissions<T> auth, AuthFailuresHandler<T> failures) {
        this.name = name;
        this.auth = auth;
        this.failures = failures;
    }

    @Override
    public boolean passes(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, T sessionState) {
        String authHeader = headers.getHeaders().get("Authorization");
        if (authHeader == null) {
            if (auth.isAthenticated(headers, sessionState)) {
                return handleAuthorization(headers, writer, sessionState);
            }
            failures.onNotYetAuthenticated(headers, writer, sessionState, name);
            return false;
        }
        int index = authHeader.indexOf("Basic ");
        if (index != 0) {
            failures.onNotYetAuthenticated(headers, writer, sessionState, name);
            return false;
        }
        String userPw = new String(Base64.getDecoder().decode(authHeader.substring(6)), Charset.forName("ASCII"));
        int colonIndex = userPw.indexOf(':');
        if (colonIndex < 0) {
            failures.onNotYetAuthenticated(headers, writer, sessionState, name);
            return false;
        }
        String username = userPw.substring(0, colonIndex);
        String pw = userPw.substring(colonIndex + 1);
        boolean authenticate = this.auth.authenticate(headers, username, pw, sessionState);
        if (!authenticate) {
            failures.onAthenticationFailure(headers, writer, sessionState, name);
            return false;
        }
        this.auth.setAthenticated(sessionState, username);
        return handleAuthorization(headers, writer, sessionState);
    }

    public boolean handleAuthorization(HttpRequest headers, HttpResponse writer, T sessionState) {
        boolean authorized = this.auth.isAuthorized(headers, sessionState);
        if (!authorized) {
            this.failures.onNotAuthorized(headers, writer, sessionState, name);
        }
        return authorized;
    }

}
