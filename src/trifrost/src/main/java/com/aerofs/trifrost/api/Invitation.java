package com.aerofs.trifrost.api;

import javax.validation.constraints.NotNull;

public class Invitation {
    @NotNull
    public String email;

    public Invitation(String email) {
        this.email = email;
    }
    @SuppressWarnings("unused")  // jackson compatibility
    private Invitation() { }
}
