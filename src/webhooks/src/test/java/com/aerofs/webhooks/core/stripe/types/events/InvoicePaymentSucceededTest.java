package com.aerofs.webhooks.core.stripe.types.events;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.aerofs.webhooks.core.stripe.types.Invoice;

public class InvoicePaymentSucceededTest {

    private static final String FIXTURE = "fixtures/InvoicePaymentSucceeded.json";
    private static StripeEvent<?> EVENT;

    @BeforeClass
    public static void setup() throws Exception {
        EVENT = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );
    }

    @Test
    public void deserializesFromJson() throws Exception {
        final StripeEvent<?> event = fromJson( jsonFixture( FIXTURE ), StripeEvent.class );

        assertThat( event ).isInstanceOf( InvoicePaymentSucceeded.class );
        assertThat( event.getObject() ).isInstanceOf( Invoice.class );
    }

    @Test
    public void hasHumanReadableToString() {
        assertThat( EVENT.toString() )
                .isEqualTo( "InvoicePaymentSucceeded{id=evt_1LBkvr5uHNqdMB, created=1361576221, livemode=false, pending_webhooks=0, data=EventData{object=Invoice{id=in_1LBkJyWTSF0YHE, date=1361576221, livemode=false, customer=cus_1LBkY3Jve4yTLJ, period_start=1361576221, period_end=1361576221, lines=StripeIterable{[LineItem{id=su_1LBk8DAhJm9Tvr, type=subscription, description=Optional.absent(), amount=0, currency=usd, period=Period{start=1361576221, end=1362785821}, proration=false, plan=Optional.of(Plan{id=business_1user, livemode=false, name=Business 1 User, created=0, trial_period_days=14, amount=1000, currency=usd, interval=month, interval_count=1}), quantity=Optional.of(1)}]}, charge=Optional.absent(), starting_balance=0, ending_balance=Optional.absent(), discount=Optional.absent(), subtotal=0, total=0, amount_due=0, currency=usd, paid=true, closed=true, attempted=true, attempt_count=0, next_payment_attempt=Optional.absent()}, previous_attributes=Optional.absent()}}" );
    }

}
