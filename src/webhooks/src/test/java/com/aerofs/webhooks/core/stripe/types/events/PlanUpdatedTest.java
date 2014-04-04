package com.aerofs.webhooks.core.stripe.types.events;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.events.PlanUpdated;

public class PlanUpdatedTest {

    private static final String FIXTURE = "fixtures/PlanUpdated.json";

    @Test
    public void canDeserializeFromJson() throws Exception {
        final StripeEvent<?> event = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );
        assertThat( event ).isInstanceOf( PlanUpdated.class );
    }

}
