package com.aerofs.webhooks.resource;

import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Matchers.any;
import static org.fest.assertions.api.Assertions.assertThat;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.yammer.dropwizard.testing.ResourceTest;

@RunWith( MockitoJUnitRunner.class )
public class StripeResourceTest extends ResourceTest {

    @Mock private ProcessContext processContext;
    private StripeResource stripeResource;

    private static final String PATH = StripeResource.ROOT_PATH;
    private static final String PING_FIXTURE = "fixtures/Ping.json";

    @Override
    protected void setUpResources() throws Exception {
        stripeResource = new StripeResource( processContext );
        addResource( stripeResource );
    }

    @Test
    public void routesPingRequestProperly() throws Exception {
        final String eventSource = jsonFixture( PING_FIXTURE );
        client().resource( PATH )
                .entity( eventSource, MediaType.APPLICATION_JSON )
                .post();
    }

    @Test
    public void respondsWith204WhenEventProcessingFails() throws Exception {
        final StripeEvent<?> mockEvent = mock( StripeEvent.class );
        stripeResource.processEvent( mockEvent );
    }

    @Test
    public void respondsWith500WhenEventProcessingFails() throws Exception {
        try {
            final StripeEvent<?> mockEvent = mock( StripeEvent.class );
            doThrow( new RuntimeException() ).when( mockEvent ).process( any( ProcessContext.class ) );

            stripeResource.processEvent( mockEvent );
        } catch ( final WebApplicationException e ) {
            assertThat( e.getResponse().getStatus() ).isEqualTo( 500 );
        }
    }

}
