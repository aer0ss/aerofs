package com.aerofs.base.analytics;

import com.aerofs.ids.UserID;

public interface IAnalyticsClient {
    void track(AnalyticsEvent event, UserID user_id, Long value);
    void track(AnalyticsEvent event, UserID user_id);
    void track(AnalyticsEvent event, Long value);
    void track(AnalyticsEvent event);
}
