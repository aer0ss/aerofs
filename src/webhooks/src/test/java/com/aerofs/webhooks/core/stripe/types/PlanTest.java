package com.aerofs.webhooks.core.stripe.types;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.Plan;
import com.aerofs.webhooks.core.stripe.types.AbstractStripeApiObject;

public class PlanTest {

    private static final String FIXTURE = "fixtures/Plan.json";

    @Test
    public void canDeserializeFromJson() throws Exception {
        final AbstractStripeApiObject stripeObject = fromJson( jsonFixture( FIXTURE ), AbstractStripeApiObject.class );
        assertThat( stripeObject ).isInstanceOf( Plan.class );
    }

}
