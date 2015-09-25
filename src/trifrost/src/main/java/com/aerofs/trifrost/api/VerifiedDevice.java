package com.aerofs.trifrost.api;

public class VerifiedDevice {
    public VerifiedDevice(String userId, String domain, String deviceId, String accessToken, long accessTokenExpiration, String refreshToken) {
        this.userId = userId;
        this.domain = domain;
        this.deviceId = deviceId;
        this.accessToken = accessToken;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshToken = refreshToken;
    }

    public String userId;
    public String domain;
    public String deviceId;
    public String accessToken;
    public long accessTokenExpiration;
    public String refreshToken;

    @SuppressWarnings("unused")
    private VerifiedDevice() { } // Jackson-compat
}
