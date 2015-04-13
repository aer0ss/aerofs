/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import com.aerofs.ids.DID;
import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.COMPLETED_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.CONTENT_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.OUTBOUND_VALUE;


public interface OutboundEventLogger
{
    int META_REQUEST = OUTBOUND_VALUE;

    int CONTENT_REQUEST = OUTBOUND_VALUE | CONTENT_VALUE;
    int CONTENT_COMPLETION = OUTBOUND_VALUE | CONTENT_VALUE | COMPLETED_VALUE;

    void log_(int type, SOID soid, DID to) throws SQLException;
}
