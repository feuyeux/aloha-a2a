package com.aloha.a2a.agent2.auth;

import io.a2a.server.auth.User;

public class SimpleUser implements User {
    
    private final String username;
    private final boolean authenticated;
    
    public SimpleUser(String username, boolean authenticated) {
        this.username = username;
        this.authenticated = authenticated;
    }
    
    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    public static SimpleUser anonymous() {
        return new SimpleUser(null, false);
    }
    
    public static SimpleUser authenticated(String username) {
        return new SimpleUser(username, true);
    }
}
