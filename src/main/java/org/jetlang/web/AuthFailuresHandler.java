package org.jetlang.web;

import java.nio.charset.Charset;

public class AuthFailuresHandler<T extends AuthenticatedUser> {
    public void onNotYetAuthenticated(HttpRequest headers, HttpResponse writer, T sessionState, String name) {
        sendUnauthorizedResponse(writer, name);
    }

    public void sendUnauthorizedResponse(HttpResponse writer, String name) {
        KeyValueList response = new KeyValueList(false);
        response.add("WWW-Authenticate", "Basic realm=\"" + name + "\"");
        writer.sendResponse(401, "Unauthorized", "text/plain", response, new byte[0], Charset.forName("UTF-8"));
    }

    public void onAthenticationFailure(HttpRequest headers, HttpResponse writer, T sessionState, String name) {
        sendUnauthorizedResponse(writer, name);
    }

    public void onNotAuthorized(HttpRequest headers, HttpResponse writer, T sessionState, String name) {
        sendUnauthorizedResponse(writer, name);
    }
}
