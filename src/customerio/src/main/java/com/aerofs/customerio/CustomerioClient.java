package com.aerofs.customerio;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.MessageBodyWriter;

import org.arrowfs.config.properties.DynamicOptionalStringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.FormMultivaluedMapProvider;
import com.yammer.metrics.annotation.Timed;

/** Customer.io REST client. The use of application/json as the request body type is not well documented at the time of
 * writing.
 * 
 * @see <a href="http://customer.io/docs/api/rest.html">Customer.io Docs</a>
 * @author Eric Schoonover <eric@aerofs.com> */
public class CustomerioClient {

    private final Client client;
    private final MessageBodyWriter<Object> messageBodyWriter;

    /** Construct a new instance that will use the provided
     * 
     * @param client Will be used to execute the Customer.io HTTP requests.
     * @param messageBodyWriter JSON body writer
     *        <p>
     *        Having to provide a MessageBodyWriter is a hack, Customer.io does not support chunked encoding which means
     *        a valid Content-Length header is required on the outgoing HTTP request. Most MessageBodyWriter
     *        implementations return 0 when the Content-Length is requested which instructs Jersey to use chunked
     *        encoding. I am using the provided MessageBodyWriter to write to a local ByteArrayOutputStream and then
     *        passing the resulting bytes to the HTTP client which causes a MessageBodyWriter to be used by Jersey that
     *        does not used chunked encoding. This hack ensures that chunked encoding is not used.
     *        </p> 
     * @throws {@link NullPointerException} if {@code client} or {@code messageBodyWriter} are null */
    @Inject
    public CustomerioClient( @Named( "customerioHttpClient" ) final Client client,
            @Named( "customerioJsonWriter" ) final MessageBodyWriter<Object> messageBodyWriter ) {
        this.client = checkNotNull( client, "client cannot be null" );
        this.messageBodyWriter = checkNotNull( messageBodyWriter, "messageBodyWriter cannot be null" );
    }

    /** Create or update a customer. Allows attributes to be passed which can then be used to personalized triggered
     * emails or affect the logic of who receives them.
     * 
     * @param customerId Used to build the URL that the request will be executed against
     *        <code>https://track.customer.io/api/v1/customers/{customerId}</code>
     * @param customer The {@link CustomerioCustomer} to register with Customer.io
     * @throws {@link BadRequestException} if {@code customer} cannot be marshaled to JSON
     * @throws {@link CustomerNotFoundException} if customer.io returns an HTTP 404 response
     * @throws {@link ServiceUnavailableException} if customer.io returns a 5xx response
     * @see <a href="http://customer.io/docs/api/rest.html#section-Creating_or_updating_customers">Customer.io Docs</a> */
    @Timed
    public void identifyCustomer( final String customerId, final CustomerioCustomer customer ) {
        checkArgument( isNotBlank( customerId ), "customerId cannot be blank" );
        checkNotNull( customer, "customer cannot be null" );

        LOGGER.debug( "Identifying Customer, customerId: " + customerId + " customer: " + customer );

        final URI customerUri = makeCustomerUri( customerId ).build();
        final WebResource resource = makeRequestResource( customerUri );
        try {
            final byte [] data = toJson( customer );
            resource.type( MediaType.APPLICATION_JSON ).put( data );
        } catch ( final IOException e ) {
            LOGGER.trace( "Exception marshalling CustomerioCustomer to JSON", e );
            throw new BadRequestException( e );
        } catch ( final UniformInterfaceException e ) {
            LOGGER.trace( "Exception making Customerio HTTP request", e );
            throw convertResourceException( e );
        }

        LOGGER.trace( "Identified Customer, customerId: " + customerId );
    }

    private UriBuilder makeCustomerUri( final String customerId ) {
        return UriBuilder.fromUri( BASE_URL ).segment( customerId );
    }

    private UriBuilder makeEventUri( final String customerId ) {
        return makeCustomerUri( customerId ).segment( "events" );
    }

    private WebResource makeRequestResource( final URI requestUri ) {
        final WebResource resource = client.resource( requestUri );
        resource.addFilter( makeBasicAuthFilter() );
        return resource;
    }

    private HTTPBasicAuthFilter makeBasicAuthFilter() {
        final Optional<String> siteId = SITE_ID_PROPERTY.get();
        checkState( siteId.isPresent(), "Dynamic Property customerio_rest_client.side_id cannot be absent" );

        final Optional<String> apiKey = API_KEY_PROPERTY.get();
        checkState( apiKey.isPresent(), "Dynamic Property customerio_rest_client.api_key cannot be absent" );

        return new HTTPBasicAuthFilter( siteId.get(), apiKey.get() );
    }

    /** We need Jersey to include the {@code Content-Length} header, which it does by default when using the
     * {@link ByteArrayProvider}. At first glance it looks like providing Jersey with an alternate
     * {@link FormMultivaluedMapProvider} would make more sense here, but that would force us to serialize
     * {@code parameters} twice (once for {@link MessageBodyWriter#getSize}, twice for {@link MessageBodyWriter#writeTo}
     * ). */
    public byte [] toJson( final Object entity ) throws IOException {
        try ( final ByteArrayOutputStream output = new ByteArrayOutputStream() ) {
            messageBodyWriter.writeTo( entity, entity.getClass(), null, null, MediaType.APPLICATION_JSON_TYPE, null,
                output );
            return output.toByteArray();
        }
    }

    private RuntimeException convertResourceException( final UniformInterfaceException e ) {
        final int responseStatus = e.getResponse().getStatus();

        if ( responseStatus == 404 ) {
            return new CustomerNotFoundException( e );
        } else if ( responseStatus >= 500 ) {
            return new ServiceUnavailableException( e );
        }

        return new BadRequestException( e );
    }

    /** Remove a customer from Customer.io
     * 
     * @param customerId Used to build the URL that the request will be executed against
     *        <code>https://track.customer.io/api/v1/customers/{customerId}</code>
     * @throws {@link CustomerNotFoundException} if customer.io returns an HTTP 404 response
     * @throws {@link ServiceUnavailableException} if customer.io returns a 5xx response
     * @see <a href="http://customer.io/docs/api/rest.html#section-Deleting_customers">Customer.io Docs</a> */
    @Timed
    public void deleteCustomer( final String customerId ) {
        checkArgument( isNotBlank( customerId ), "customerId cannot be blank" );

        LOGGER.debug( "Deleting Customer, customerId: " + customerId );

        final URI customerUri = makeCustomerUri( customerId ).build();
        final WebResource resource = makeRequestResource( customerUri );
        try {
            resource.delete();
        } catch ( final UniformInterfaceException e ) {
            LOGGER.trace( "Exception making Customerio HTTP request", e );
            throw convertResourceException( e );
        }

        LOGGER.trace( "Deleted Customer, customerId: " + customerId );
    }

    /** Send an event to Customer.io
     * 
     * @param customerId Used to build the URL that the request will be executed against
     *        <code>https://track.customer.io/api/v1/customers/{customerId}/events</code>
     * @param event The {@link CustomerioEvent} instance to associate with the specified {@code customerId}
     * @throws {@link BadRequestException} if {@code event} cannot be marshaled to JSON
     * @throws {@link CustomerNotFoundException} if customer.io returns an HTTP 404 response
     * @throws {@link ServiceUnavailableException} if customer.io returns a 5xx response
     * @see <a href="http://customer.io/docs/api/rest.html#section-Track_a_custom_event">Customer.io Docs</a> */
    @Timed
    public void trackEvent( final String customerId, final CustomerioEvent<?> event ) {
        checkArgument( isNotBlank( customerId ), "customerId cannot be blank" );
        checkNotNull( event, "event cannot be null, use Optional.absent() instead" );

        LOGGER.debug( "Tracking Event, customerId: " + customerId + " event: " + event );

        final URI customerEventsUri = makeEventUri( customerId ).build();
        final WebResource resource = makeRequestResource( customerEventsUri );

        try {
            final byte [] data = toJson( event );
            resource.type( MediaType.APPLICATION_JSON ).post( data );
        } catch ( final IOException e ) {
            LOGGER.trace( "Exception marshalling CustomerioEvent to JSON", e );
            throw new BadRequestException( e );
        } catch ( final UniformInterfaceException e ) {
            LOGGER.trace( "Exception making Customerio HTTP request", e );
            throw convertResourceException( e );
        }

        LOGGER.trace( "Tracked Event, customerId: " + customerId );
    }

    private static final DynamicOptionalStringProperty SITE_ID_PROPERTY =
            new DynamicOptionalStringProperty( "customerio_rest_client.site_id" );
    private static final DynamicOptionalStringProperty API_KEY_PROPERTY =
            new DynamicOptionalStringProperty( "customerio_rest_client.api_key" );

    private static final String BASE_URL = "https://track.customer.io/api/v1/customers/";

    private static final Logger LOGGER = LoggerFactory.getLogger( CustomerioClient.class );

}
