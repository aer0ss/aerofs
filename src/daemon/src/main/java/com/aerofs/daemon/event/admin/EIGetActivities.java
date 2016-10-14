/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;

import javax.annotation.Nullable;
import java.util.List;

public class EIGetActivities extends AbstractEBIMC
{
    public final boolean _brief;
    public final Long _pageToken;
    public final int _maxResults;

    public List<PBActivity> _activities;
    public @Nullable Long _replyPageToken;
    public boolean _hasUnresolvedDevices;

    public EIGetActivities(boolean brief, @Nullable Long pageToken, int maxResults,
            IIMCExecutor imce)
    {
        super(imce);
        _brief = brief;
        _pageToken = pageToken;
        _maxResults = maxResults;
    }

    public void setResult_(List<PBActivity> activities, @Nullable Long replyPageToken,
            boolean hasUnresolvedDevices)
    {
        _activities = activities;
        _replyPageToken = replyPageToken;
        _hasUnresolvedDevices = hasUnresolvedDevices;
    }
}
