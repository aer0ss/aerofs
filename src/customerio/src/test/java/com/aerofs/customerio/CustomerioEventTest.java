package com.aerofs.customerio;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.fest.assertions.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class CustomerioEventTest {

    private static final String FIXTURE_WITH_DATA = "fixtures/CustomerioEventWithData.json";
    private static final String FIXTURE_WITHOUT_DATA = "fixtures/CustomerioEventWithoutData.json";

    private static final String NAME = "ping";

    private static final Map<String, Object> eventData =
            ImmutableMap.<String, Object> of( "string", "hello",
                "number", new int [] { 5679, 98765 },
                "map", ImmutableMap.of(
                    "string", "world",
                    "boolean", true ) );
    private static final CustomerioEvent<?> EVENT_WITH_DATA = new CustomerioEvent<>( NAME, Optional.of( eventData ) );
    private static final CustomerioEvent<?> EVENT_WITHOUT_DATA = new CustomerioEvent<>( NAME, Optional.absent() );

    @Test( expected = IllegalArgumentException.class )
    public void illegalArgumentExceptionWhenConstructedWithNullName() {
        new CustomerioEvent<>( null, Optional.absent() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void illegalArgumentExceptionWhenConstructedWithEmptyName() {
        new CustomerioEvent<>( "", Optional.absent() );
    }

    @Test( expected = NullPointerException.class )
    public void nullPointerExceptionWhenConstructedWithNullAttributes() {
        new CustomerioEvent<>( NAME, null );
    }

    @Test
    public void serializesToJsonWithData() throws Exception {
        final String json = asJson( EVENT_WITH_DATA );
        assertThat( json ).isEqualTo( jsonFixture( FIXTURE_WITH_DATA ) );
    }

    @Test
    public void serializesToJsonWithoutData() throws Exception {
        final String json = asJson( EVENT_WITHOUT_DATA );
        assertThat( json ).isEqualTo( jsonFixture( FIXTURE_WITHOUT_DATA ) );
    }

}
