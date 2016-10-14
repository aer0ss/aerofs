package com.aerofs.sp.server.lib;


import static com.aerofs.base.config.ConfigurationProperties.getNonEmptyStringProperty;

public enum NewUserInviteRestriction
{
    UNRESTRICTED,
    USER_INVITED,
    ADMIN_INVITED;

    public static NewUserInviteRestriction getRestrictionLevel() {
        return valueOf(getNonEmptyStringProperty("signup_restriction", "USER_INVITED"));
    }
}
