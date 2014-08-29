/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.analytics;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class is responsible for sending events to our analytics backend. Currently, we use
 * Mixpanel.
 */
public class Analytics
{
    private static final Logger l = Loggers.getLogger(Analytics.class);
    private static final String MIXPANEL_TOKEN="be2b9a3bb410c5183c66523483cb27de";
    private final ListeningExecutorService _executor;

    private static class Properties
    {
        private static final String
                TEAM_ID     = "team_id",
                USER_ID     = "user_id",
                DID         = "did",
                VERSION     = "version",
                OS_FAMILY   = "os_family",
                OS_NAME     = "os_name",
                SIGNUP_DATE = "signup_date";
    }

    private final SimpleDateFormat _dateformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final MixpanelAPI _mixpanel;
    private final IAnalyticsPlatformProperties _properties;

    @Inject
    public Analytics(IAnalyticsPlatformProperties properties)
    {
        _properties = properties;
        _mixpanel = new MixpanelAPI(MIXPANEL_TOKEN);
        _dateformat.setTimeZone(TimeZone.getTimeZone("UTC"));

        _executor = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }));
    }

    /**
     * Send an analytics event asynchronously
     * Note: do not make a sync (blocking) version of this call, to avoid performance costs when
     * Analytics are not available (ie: in deployed environments)
     */
    public void track(IAnalyticsEvent event)
    {
        Futures.addCallback(trackImpl(event), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(@Nonnull Void v) { }

            @Override
            public void onFailure(@Nonnull Throwable e)
            {
                l.warn("sending analytics failed: {}", e.toString()); // do not print stack trace
            }
        });
    }

    private ListenableFuture<Void> trackImpl(final IAnalyticsEvent event)
    {
        return _executor.submit(() -> {
            Map<String, String> properties = Maps.newHashMap();

            UserID user = _properties.getUserID();
            DID did = _properties.getDid();

            String userStr = (user != null) ? user.getString() : null;
            String didStr = (did != null) ? did.toStringFormal() : null;

            // teamID cannot be null. If the teamID is null, we should not put things into
            // Analytics as the teamID is used as a unique identifier.

            String teamID = checkNotNull(_properties.getOrgID());

            // Add the default properties that we send with all events
            addIfNonNull(properties, Properties.USER_ID, userStr);
            addIfNonNull(properties, Properties.DID, didStr);
            addIfNonNull(properties, Properties.VERSION, _properties.getVersion());
            addIfNonNull(properties, Properties.OS_FAMILY, _properties.getOSFamily());
            addIfNonNull(properties, Properties.OS_NAME, _properties.getOSName());
            addIfNonNull(properties, Properties.TEAM_ID, teamID);

            long signupDate = _properties.getSignupDate();
            if (signupDate > 0) {
                properties.put(Properties.SIGNUP_DATE, _dateformat.format(new Date(signupDate)));
            }

            // Add any additional property supplied by the event
            event.saveProperties(properties);

            _mixpanel.track(event.getName(), teamID, properties);

            return null;
        });

    }

    private void addIfNonNull(Map<String, String> properties, String key, @Nullable String value)
    {
        if (value != null) properties.put(key, value);
    }
}
