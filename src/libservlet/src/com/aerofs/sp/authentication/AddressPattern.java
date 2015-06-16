/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.ids.UserID;
import org.apache.commons.lang.StringUtils;

import java.util.regex.Pattern;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Check user ids against a configured pattern to filter internal addresses.
 */
class AddressPattern
{
    AddressPattern()
    {
        _internalAddressPattern = StringUtils.isNotEmpty(getStringProperty(PATTERN_PARAM, ""))
                ? Pattern.compile(getStringProperty(PATTERN_PARAM, ""))
                : null;
    }

    /**
     * A user is internal if any of:
     *  - this is a public deployment;
     *  - there is no configured internal address pattern;
     *  - the user's address matches the internal address pattern.
     */
    boolean isInternalUser(UserID userID)
    {
        assert !userID.isTeamServerID();
        return (_internalAddressPattern == null)
                || _internalAddressPattern.matcher(userID.getString()).matches();
    }

    private static final String PATTERN_PARAM = "internal_email_pattern";
    private final Pattern _internalAddressPattern;
}
