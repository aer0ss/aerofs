package com.aerofs.trifrost.api;

/**
 */
public class Invitation {
    public Invitation(String email) {
        this.email = email;
    }
    private Invitation() { } // jackson-compat

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    private String email;
}
