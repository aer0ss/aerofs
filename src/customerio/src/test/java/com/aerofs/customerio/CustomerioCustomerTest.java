package com.aerofs.customerio;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class CustomerioCustomerTest {

    private static final String FIXTURE_WITH_ATTRIBUTES = "fixtures/CustomerioCustomerWithAttributes.json";
    private static final String FIXTURE_WITHOUT_ATTRIBUTES = "fixtures/CustomerioCustomerWithoutAttributes.json";

    private static final String EMAIL = "eric+test@aerofs.com";

    private static final Map<String, Object> attributes =
            ImmutableMap.<String, Object> of( "string", "hello",
                "number", new int [] { 5679, 98765 },
                "map", ImmutableMap.of(
                    "string", "world",
                    "boolean", true ) );
    private static final CustomerioCustomer CUSTOMER_WITH_ATTRIBUTES = new CustomerioCustomer( EMAIL, Optional.of( attributes ) );
    private static final CustomerioCustomer CUSTOMER_WITHOUT_ATTRIBUTES = new CustomerioCustomer( EMAIL, Optional.<Map<String, Object>> absent() );

    @Test( expected = IllegalArgumentException.class )
    public void illegalArgumentExceptionWhenConstructedWithNullEmail() {
        new CustomerioCustomer( null, Optional.<Map<String, Object>> absent() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void illegalArgumentExceptionWhenConstructedWithEmptyEmail() {
        new CustomerioCustomer( "", Optional.<Map<String, Object>> absent() );
    }

    @Test( expected = NullPointerException.class )
    public void nullPointerExceptionWhenConstructedWithNullAttributes() {
        new CustomerioCustomer( EMAIL, null );
    }

    @Test
    public void serializesToJsonWithAttribtues() throws Exception {
        final String json = asJson( CUSTOMER_WITH_ATTRIBUTES );
        assertThat( json ).isEqualTo( jsonFixture( FIXTURE_WITH_ATTRIBUTES ) );
    }

    @Test
    public void serializesToJsonWithoutAttributes() throws Exception {
        final String json = asJson( CUSTOMER_WITHOUT_ATTRIBUTES );
        assertThat( json ).isEqualTo( jsonFixture( FIXTURE_WITHOUT_ATTRIBUTES ) );
    }
}
