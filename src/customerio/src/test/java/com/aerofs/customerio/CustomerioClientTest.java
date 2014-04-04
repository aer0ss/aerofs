package com.aerofs.customerio;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

import org.arrowfs.config.ArrowConfiguration;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.json.ObjectMapperFactory;

@RunWith( MockitoJUnitRunner.class )
public class CustomerioClientTest {

    /** This is an integration test, does not need to be run as part of normal developer unit testing. */
    @Ignore
    @Test
    public void testRealClient() {
        final String emailToKeep = "eric+integration-test-customerio@aerofs.com";
        final String emailToDelete = "eric+integration-test-customerio-deleteme@aerofs.com ";

        final Client httpClient = Client.create( new DefaultClientConfig() );
        final JacksonMessageBodyProvider messageBodyProvider = new JacksonMessageBodyProvider(
                new ObjectMapperFactory().build(), null );
        final CustomerioClient client = new CustomerioClient( httpClient, messageBodyProvider );

        final CustomerioCustomer customerToDelete = new CustomerioCustomer( emailToDelete,
                Optional.<Map<String, Object>> absent() );
        final Map<String, Object> attributes =
                ImmutableMap.<String, Object> of( "string", "hello",
                    "number", new int [] { 5679, 98765 },
                    "map", ImmutableMap.of(
                        "string", "world",
                        "boolean", true ) );
        final CustomerioCustomer customerToKeep = new CustomerioCustomer( emailToKeep, Optional.of( attributes ) );

        client.identifyCustomer( emailToDelete, customerToDelete );
        client.identifyCustomer( emailToKeep, customerToKeep );

        client.trackEvent( emailToKeep, new CustomerioEvent<>( "ping", Optional.absent() ) );

        final Map<String, Object> eventData = ImmutableMap.<String, Object> of( "map",
            ImmutableMap.of( "echo", "EVERYTHING IS WORKING" ) );
        client.trackEvent( emailToKeep, new CustomerioEvent<>( "__integration-test", Optional.of( eventData ) ) );

        client.deleteCustomer( emailToDelete );
    }

    private static final String EMAIL = "eric+test@aerofs.com";

    private static final CustomerioCustomer CUSTOMER = new CustomerioCustomer( EMAIL,
            Optional.<Map<String, Object>> absent() );
    private static final CustomerioEvent<?> EVENT = new CustomerioEvent<>( EMAIL, Optional.absent() );

    @Mock private Client mockClient;
    @Mock private WebResource mockWebResource;
    @Mock private WebResource.Builder mockWebResourceBuilder;

    @Mock private MessageBodyWriter<Object> mockMessageBodyWriter;

    @BeforeClass
    public static void setup() {
        ArrowConfiguration.initialize( ArrowConfiguration.builder().build() );
        ArrowConfiguration.getInstance().setProperty( "customerio_rest_client.site_id", "test" );
        ArrowConfiguration.getInstance().setProperty( "customerio_rest_client.api_key", "test" );
    }

    @Test( expected = IllegalArgumentException.class )
    public void identifyCustomerFailsWithIllegalArgumentExceptionWhenCustomerIdIsNull() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.identifyCustomer( null, CUSTOMER );
    }

    @Test( expected = IllegalArgumentException.class )
    public void identifyCustomerFailsWithIllegalArgumentExceptionWhenCustomerIdIsEmpty() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.identifyCustomer( "", CUSTOMER );
    }

    @Test( expected = NullPointerException.class )
    public void identifyCustomerFailsWithNullPointerExceptionWhenCustomerIsNull() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.identifyCustomer( EMAIL, null );
    }

    @Test
    public void executesIdentifyCustomerHttpRequest() throws Exception {
        doReturn( mockWebResource ).when( mockClient ).resource( any( URI.class ) );
        doReturn( mockWebResourceBuilder ).when( mockWebResource ).type( MediaType.APPLICATION_JSON );

        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.identifyCustomer( EMAIL, CUSTOMER );

        verifyResourceWithCustomerUrl();
        verifyHttpBasicAuthFilter();
        verifyEntityToJson();
        verifyContentType();
        verify( mockWebResourceBuilder ).put( any( byte [].class ) );
    }

    private void verifyResourceWithCustomerUrl() {
        verify( mockClient ).resource( URI.create( "https://track.customer.io/api/v1/customers/" + EMAIL ) );
    }

    private void verifyHttpBasicAuthFilter() {
        verify( mockWebResource ).addFilter( any( HTTPBasicAuthFilter.class ) );
    }

    @SuppressWarnings( "unchecked" )
    private void verifyEntityToJson() throws IOException {
        verify( mockMessageBodyWriter ).writeTo( any( Object.class ), any( Class.class ),
            any( Type.class ), any( Annotation [].class ), any( MediaType.class ), any( MultivaluedMap.class ),
            any( OutputStream.class ) );
    }

    private void verifyContentType() {
        verify( mockWebResource ).type( MediaType.APPLICATION_JSON );
    }

    @Test( expected = IllegalArgumentException.class )
    public void deleteCustomerFailsWithIllegalArgumentExceptionWhenCustomerIdIsNull() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.deleteCustomer( null );
    }

    @Test( expected = IllegalArgumentException.class )
    public void deleteCustomerFailsWithIllegalArgumentExceptionWhenCustomerIdIsEmpty() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.deleteCustomer( "" );
    }

    @Test
    public void executesDeleteCustomerHttpRequest() throws Exception {
        doReturn( mockWebResource ).when( mockClient ).resource( any( URI.class ) );

        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.deleteCustomer( EMAIL );

        verifyResourceWithCustomerUrl();
        verifyHttpBasicAuthFilter();
        verify( mockWebResource ).delete();
    }

    @Test( expected = IllegalArgumentException.class )
    public void trackEventFailsWithIllegalArgumentExceptionWhenCustomerIdIsNull() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.trackEvent( null, EVENT );
    }

    @Test( expected = IllegalArgumentException.class )
    public void trackEventFailsWithIllegalArgumentExceptionWhenCustomerIdIsEmpty() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.trackEvent( "", EVENT );
    }

    @Test( expected = NullPointerException.class )
    public void trackEventFailsWithNullPointerExceptionWhenEventIsNull() {
        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.trackEvent( EMAIL, null );
    }

    @Test
    public void executesTrackEventHttpRequest() throws Exception {
        doReturn( mockWebResource ).when( mockClient ).resource( any( URI.class ) );
        doReturn( mockWebResourceBuilder ).when( mockWebResource ).type( MediaType.APPLICATION_JSON );

        final CustomerioClient client = new CustomerioClient( mockClient, mockMessageBodyWriter );
        client.trackEvent( EMAIL, EVENT );

        verify( mockClient ).resource( URI.create( "https://track.customer.io/api/v1/customers/" + EMAIL + "/events" ) );
        verifyHttpBasicAuthFilter();
        verifyEntityToJson();
        verifyContentType();
        verify( mockWebResourceBuilder ).post( any( byte [].class ) );
    }

}
