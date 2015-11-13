package com.aerofs.trifrost.api;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel()
public class VerifiedDevice {
    public VerifiedDevice(String userId, String domain, String deviceId, String accessToken, long accessTokenExpiration, String refreshToken) {
        this.userId = userId;
        this.domain = domain;
        this.deviceId = deviceId;
        this.accessToken = accessToken;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshToken = refreshToken;
    }

    public VerifiedDevice() { }

    @ApiModelProperty(value = "unique user identifier")
    public String userId;
    @ApiModelProperty(value = "domain string for xmpp connections")
    public String domain;
    @ApiModelProperty(value = "device identifier, unique for this user")
    public String deviceId;
    @ApiModelProperty(value = "time-limited authorization token")
    public String accessToken;
    @ApiModelProperty(value = "expiration date of the access token, in milliseconds since the Epoch")
    public long accessTokenExpiration;
    @ApiModelProperty(value = "single-use refresh token")
    public String refreshToken;

}
