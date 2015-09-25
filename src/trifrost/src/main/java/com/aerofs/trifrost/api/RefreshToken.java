package com.aerofs.trifrost.api;

import javax.validation.constraints.NotNull;

public class RefreshToken {
    @SuppressWarnings("unused")
    private RefreshToken() { };

    public RefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @NotNull
    public String refreshToken;
}
