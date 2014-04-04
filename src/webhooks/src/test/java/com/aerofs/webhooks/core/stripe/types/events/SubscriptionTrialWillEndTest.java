package com.aerofs.webhooks.core.stripe.types.events;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.Subscription;

public class SubscriptionTrialWillEndTest {

    private static final String FIXTURE = "fixtures/SubscriptionTrialWillEnd.json";
    private static StripeEvent<?> EVENT;

    @BeforeClass
    public static void setup() throws Exception {
        EVENT = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );
    }

    @Test
    public void deserializesFromJson() throws Exception {
        final StripeEvent<?> event = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );

        assertThat( event ).isInstanceOf( SubscriptionTrialWillEnd.class );
        assertThat( event.getObject() ).isInstanceOf( Subscription.class );
    }

    @Test
    public void hasHumanReadableToString() {
        assertThat( EVENT.toString() )
                .isEqualTo( "SubscriptionTrialWillEnd{id=evt_1M5dgNQyO6wxFM, created=1361784104, livemode=true, pending_webhooks=1, data=EventData{object=Subscription{customer=cus_1Hy6rp3iQDXy3v, current_period_start=Optional.of(1360833524), current_period_end=Optional.of(1362043124), plan=Plan{id=business_1user, livemode=true, name=Business 1 User, created=0, trial_period_days=14, amount=1000, currency=usd, interval=month, interval_count=1}, quantity=1, start=1360833524, status=trialing, cancel_at_period_end=false, canceled_at=Optional.absent(), ended_at=Optional.absent(), trial_start=Optional.of(1360833524), trial_end=Optional.of(1362043124)}, previous_attributes=Optional.absent()}}" );
    }

}
