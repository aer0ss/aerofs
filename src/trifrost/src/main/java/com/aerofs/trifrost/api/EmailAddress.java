package com.aerofs.trifrost.api;

import javax.validation.constraints.NotNull;

public class EmailAddress {
    @NotNull
    public String email;

    public EmailAddress(String email) { this.email = email; }
    @SuppressWarnings("unused")  // Jackson compatibility
    private EmailAddress() { }
}
