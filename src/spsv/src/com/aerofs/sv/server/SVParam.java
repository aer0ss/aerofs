/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.server;

public class SVParam
{
    // Database.
    public static final String SV_DATABASE_REFERENCE_PARAMETER = "sv_database_resource_reference";

    // Notifications.
    // TODO (MP) if we ship SV in private deployements we will need these to be dynamic.
    public static final String SV_NOTIFICATION_SENDER = "sv@aerofs.com";
    public static final String SV_NOTIFICATION_RECEIVER = "defects@aerofs.com";
}
