package com.aerofs.trifrost.api;

import javax.validation.constraints.NotNull;

public class RefreshToken {
    @NotNull
    public String refreshToken;

    @SuppressWarnings("unused")
    private RefreshToken() { }
    public RefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
