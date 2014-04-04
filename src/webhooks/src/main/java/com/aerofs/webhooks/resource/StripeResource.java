package com.aerofs.webhooks.resource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.core.stripe.types.StripeEvent;
import com.google.common.annotations.VisibleForTesting;
import com.yammer.dropwizard.hibernate.UnitOfWork;
import com.yammer.metrics.annotation.Timed;

@Path( StripeResource.ROOT_PATH )
public class StripeResource {

    private final ProcessContext context;

    @Inject
    public StripeResource( final ProcessContext context ) {
        this.context = context;
    }

    @POST
    @Consumes( MediaType.APPLICATION_JSON )
    @UnitOfWork
    @Timed
    public void processEvent( final StripeEvent<?> event ) {
        try {
            LOGGER.info( "Processing event: " + event );
            event.process( context );
        } catch ( final Exception e ) {
            LOGGER.error( "Failed to process event: " + event, e );
            throw new WebApplicationException( 500 );
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger( StripeResource.class );
    @VisibleForTesting static final String ROOT_PATH = "/stripe";

}
