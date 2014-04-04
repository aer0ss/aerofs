package com.aerofs.webhooks.core.stripe.types.events;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.Plan;
import com.aerofs.webhooks.core.stripe.types.events.PlanCreated;

public class PlanCreatedTest {

    private static final String FIXTURE = "fixtures/PlanCreated.json";
    private static StripeEvent<?> EVENT;

    @BeforeClass
    public static void setup() throws Exception {
        EVENT = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );
    }

    @Test
    public void canDeserializeFromJson() throws Exception {
        assertThat( EVENT ).isInstanceOf( PlanCreated.class );
        assertThat( EVENT.getObject() ).isInstanceOf( Plan.class );
    }

    @Test
    public void hasHumanReadableToString() {
        assertThat( EVENT.toString() )
                .isEqualTo( "PlanCreated{id=evt_1HOth4veBZqqKV, created=1360702530, livemode=false, " +
                        "pending_webhooks=0, data=EventData{object=Plan{id=business_50user, livemode=false, " +
                        "name=Business 50 User, created=0, trial_period_days=14, amount=50000, currency=usd, " +
                        "interval=month, interval_count=1}, previous_attributes=Optional.absent()}}" );
    }

}
