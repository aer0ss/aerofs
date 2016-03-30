package com.aerofs.servlets.lib.analytics;

import com.aerofs.ids.UserID;

import java.io.IOException;

public interface IAnalyticsClient {
    void track(AnalyticsEvent event, UserID user_id) throws IOException;
    void track(AnalyticsEvent event) throws IOException;
}
