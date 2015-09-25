package com.aerofs.trifrost.api;

/**
 */
public class EmailAddress {
    public EmailAddress(String email) { this.email = email; }
    private EmailAddress() { } // Jackson-compat

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    private String email;
}
