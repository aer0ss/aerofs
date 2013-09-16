/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.common;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;

import java.util.regex.Pattern;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Distinguish internal users from external users.
 *
 * A user is internal if:
 *
 * - we have no internal mail address pattern; or,
 *
 * - the user is a team server user; or,
 *
 * - the user id matches the configured pattern.
 */
public class UserFilter
{
    /**
     * Internal users must have a userid that matches the provided pattern,
     * or be a team-server user.
     */
    public UserFilter()
    {
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT
                && !getStringProperty(INTERNAL_PATTERN_PARAM, "").isEmpty())
        {
            _internalAddressPattern = Pattern.compile(
                    getStringProperty(INTERNAL_PATTERN_PARAM, ""));
        } else {
            _internalAddressPattern = null;
        }
    }

    public boolean isInternalUser(UserID id)
    {
        // Ignore Team Server IDs. If the ID identifies a Team Server for a different organization
        // than the sharer's organization, the system guarantees that there must be at least one
        // non-Team Server user who belongs to that Team Server's organization.
        return _internalAddressPattern == null
                || id.isTeamServerID()
                || _internalAddressPattern.matcher(id.getString()).matches();
    }

    private final Pattern _internalAddressPattern;
    private static final String INTERNAL_PATTERN_PARAM = "internal_email_pattern";
}
