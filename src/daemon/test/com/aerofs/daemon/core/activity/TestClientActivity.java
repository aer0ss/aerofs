/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import org.junit.Test;

import static com.aerofs.daemon.core.activity.ClientActivity.CONTENT_COMPLETED;
import static com.aerofs.daemon.core.activity.ClientActivity.CONTENT_REQUEST;
import static com.aerofs.daemon.core.activity.ClientActivity.CREATE;
import static com.aerofs.daemon.core.activity.ClientActivity.DELETE;
import static com.aerofs.daemon.core.activity.ClientActivity.META_REQUEST;
import static com.aerofs.daemon.core.activity.ClientActivity.MODIFY;
import static com.aerofs.daemon.core.activity.ClientActivity.MOVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public final class TestClientActivity
{
    @Test
    public void shouldConvertGetActivitiesReplyTypesIntoCorrectClientActivityTypes()
    {
        assertThat(ClientActivity.getIndicatedActivities(1) , containsInAnyOrder(CREATE));
        assertThat(ClientActivity.getIndicatedActivities(2) , containsInAnyOrder(MODIFY));
        assertThat(ClientActivity.getIndicatedActivities(3) , containsInAnyOrder(CREATE, MODIFY));
        assertThat(ClientActivity.getIndicatedActivities(4) , containsInAnyOrder(MOVE));
        assertThat(ClientActivity.getIndicatedActivities(5) , containsInAnyOrder(CREATE, MOVE));
        assertThat(ClientActivity.getIndicatedActivities(6) , containsInAnyOrder(MODIFY, MOVE));
        assertThat(ClientActivity.getIndicatedActivities(7) , containsInAnyOrder(CREATE, MODIFY, MOVE));
        assertThat(ClientActivity.getIndicatedActivities(8) , containsInAnyOrder(DELETE));
        assertThat(ClientActivity.getIndicatedActivities(9) , containsInAnyOrder(CREATE, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(10), containsInAnyOrder(MODIFY, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(11), containsInAnyOrder(CREATE, MODIFY, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(12), containsInAnyOrder(MOVE, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(13), containsInAnyOrder(CREATE, MOVE, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(14), containsInAnyOrder(MODIFY, MOVE, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(15), containsInAnyOrder(CREATE, MODIFY, MOVE, DELETE));
        assertThat(ClientActivity.getIndicatedActivities(16), containsInAnyOrder(META_REQUEST));
        // at some point we may insert META_COMPLETED at position 17
        assertThat(ClientActivity.getIndicatedActivities(18), containsInAnyOrder(CONTENT_REQUEST));
        assertThat(ClientActivity.getIndicatedActivities(19), containsInAnyOrder(CONTENT_COMPLETED));
    }
}
