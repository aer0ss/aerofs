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
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * This class is responsible for sending events to our analytics backend. Currently, we use
 * Mixpanel.
 */
public class Analytics
{
    private static final Logger l = Loggers.getLogger(Analytics.class);
    private static final String MIXPANEL_TOKEN="592e47220d721ecd03e323a92fd712cf";

    private static class Properties
    {
        private static final String
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
    }

    /**
     * Send an analytics event asynchronously
     */
    public void track(IAnalyticsEvent event)
    {
        Futures.addCallback(trackImpl(event), new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v) { }

            @Override
            public void onFailure(Throwable e)
            {
                logError(e);
            }
        });
    }

    /**
     * Send an analytics event synchronously
     */
    public void trackSync(IAnalyticsEvent event)
    {
        try {
            trackImpl(event).get();
        } catch (Exception e) {
            logError(e);
        }
    }

    private void logError(Throwable e)
    {
        l.warn("sending analytics failed: {}", e.getMessage()); // do not print stack trace
    }

    private ListenableFuture<Void> trackImpl(IAnalyticsEvent event)
    {
        Map<String, String> properties = Maps.newHashMap();

        UserID user = _properties.getUser();
        DID did = _properties.getDid();

        String userStr = (user != null) ? user.getString() : null;
        String didStr = (did != null) ? did.toStringFormal() : null;

        // Add the default properties that we send with all events
        addIfNonNull(properties, Properties.USER_ID, userStr);
        addIfNonNull(properties, Properties.DID, didStr);
        addIfNonNull(properties, Properties.VERSION, _properties.getVersion());
        addIfNonNull(properties, Properties.OS_FAMILY, _properties.getOSFamily());
        addIfNonNull(properties, Properties.OS_NAME, _properties.getOSName());

        long signupDate = _properties.getSignupDate();
        if (signupDate > 0) {
            properties.put(Properties.SIGNUP_DATE, _dateformat.format(new Date(signupDate)));
        }

        // Add any additional property supplied by the event
        event.saveProperties(properties);

        return _mixpanel.track(event.getName(), userStr, properties);
    }

    private void addIfNonNull(Map<String, String> properties, String key, @Nullable String value)
    {
        if (value != null) properties.put(key, value);
    }
}
