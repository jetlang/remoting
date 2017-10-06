package org.jetlang.web;

public interface AuthenticatedUser {

    boolean isAuthenticated();

    void setAuthenticated(String user);

    String getAuthenticatedUser();

    class BasicUser implements AuthenticatedUser {

        private String user;

        @Override
        public boolean isAuthenticated() {
            return user != null;
        }

        @Override
        public void setAuthenticated(String user) {
            this.user = user;
        }

        @Override
        public String getAuthenticatedUser() {
            return this.user;
        }
    }
}
