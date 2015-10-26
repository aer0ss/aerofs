/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


public class Invitee {
    public final String emailTo;
    public final String emailFrom;
    public final String signupCode;

    public Invitee(String emailTo, String emailFrom, String signupCode) {
        this.emailTo = emailTo;
        this.emailFrom = emailFrom;
        this.signupCode = signupCode;
    }
}
