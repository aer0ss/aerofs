package com.aerofs.webhooks.core.stripe.types.events;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.InvoiceItem;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;

public class InvoiceItemCreatedTest {

    private static final String FIXTURE = "fixtures/InvoiceItemCreated.json";
    private static StripeEvent<?> EVENT;

    @BeforeClass
    public static void setup() throws Exception {
        EVENT = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );
    }

    @Test
    public void deserializesFromJson() throws Exception {
        final StripeEvent<?> event = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );

        assertThat( event ).isInstanceOf( InvoiceItemCreated.class );
        assertThat( event.getObject() ).isInstanceOf( InvoiceItem.class );
    }

    @Test
    public void hasHumanReadableToString() {
        assertThat( EVENT.toString() )
                .isEqualTo( "InvoiceItemCreated{id=evt_1MLnwTVkmCGdcY, created=1361844245, livemode=false, pending_webhooks=2, data=EventData{object=InvoiceItem{id=ii_1MLnnqGNVGgvK1, description=Optional.of(Remaining time on Business 40 User after 26 Feb 2013), amount=35562, currency=usd, proration=true, plan=Optional.absent(), quantity=Optional.absent()}, previous_attributes=Optional.absent()}}" );
    }

}
