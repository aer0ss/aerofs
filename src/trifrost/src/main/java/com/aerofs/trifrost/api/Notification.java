package com.aerofs.trifrost.api;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

public class Notification {
    @NotNull
    @ApiModelProperty("alert message content")
    public String alert;
    @NotNull
    @ApiModelProperty("target user id (jid)")
    public String target;

    @SuppressWarnings("unused") // Jackson compatibility
    private Notification() { }
}
