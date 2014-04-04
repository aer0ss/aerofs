package com.aerofs.webhooks.core.stripe.types;

import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.Test;

public class DiscountTest {

    private static final String FIXTURE = "fixtures/Discount.json";

    @Test
    public void canDeserializeFromJson() throws Exception {
        final Discount discount = fromJson( jsonFixture( FIXTURE ), Discount.class );
        assertThat( discount ).isInstanceOf( Discount.class );
    }

}
