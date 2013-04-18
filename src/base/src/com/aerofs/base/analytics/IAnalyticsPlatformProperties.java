/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.analytics;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

import javax.annotation.Nullable;

/**
 * Interface for properties that are sent with all analytics events, but that are defined
 * in a platform-specific way (ie: android vs desktop).
 */
public interface IAnalyticsPlatformProperties
{
    /**
     * User id, or null if not signed up yet or this information isn't available
     */
    @Nullable UserID getUser();

    /**
     * did, or null if not signed up yet or this information isn't available
     */
    @Nullable DID getDid();

    /**
     * A string that identifies one specific release of one specific app.
     * E.g: "0.4.150" for the desktop app, "android 0.3.20-beta" for the Android app, etc...
     * Use null if this information isn't available.
     */
    @Nullable String getVersion();

    /**
     * OS family, without any version information. E.g: Windows, Mac OS X, Linux, Android, etc...
     * Use null if this information isn't available.
     */
    @Nullable String getOSFamily();

    /**
     * Full OS name, including version information
     * Use null if this information isn't available.
     */
    @Nullable String getOSName();

    /**
     * Timestamp at which the user was created, or 0 if not available
     */
    long getSignupDate();
}
