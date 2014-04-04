### A RESTful client implemented in Java for Customerio

* Identify Customers, optionally include custom attributes
* Delete Customers
* Track Customer Events, optionally include custom attributes

Designed to be used within Dropwizard based services. Optionally support dependency injection via Google Guice.

### Requires

The customer.io SSL cert must be added to the Java truststore.

```bash
sudo keytool -import -file ./customerio.cert -alias "customerio" -keystore $JAVA_HOME/jre/lib/security/cacerts
# default password is 'changeit'
```

### Usage

#### Maven Dependency

###### Repositories
    http://repos.arrowfs.org/nexus/content/repositories/snapshots
    http://repos.arrowfs.org/nexus/content/repositories/releases

###### Dependency
    com.aerofs.customerio:customerio:latest.snapshot

#### Construct a new `com.aerofs.customerio.CustomerioClient`.

[JavaDoc][0]  

```java
// a Dropwizard service implementation
public class ExampleService extends Service<ExampleConfiguration> {

    // implementation truncated

    @Override
    public void run( final ExampleConfiguration configuration, final Environment environment ) {
        final Client httpClient = new JerseyClientBuilder()
                .using( environment )
                .using( configuration.getCustomerioHttpClientConfiguration() )
                .build();

        final MessageBodyWriter<Object> bodyWriter = new JacksonMessageBodyProvider(
                environment.getObjectMapperFactory().build(), environment.getValidator() );

        final CustomerioClient customerioClient = new CustomerioClient( httpClient, bodyWriter );
    }
    
}
```

#### Use Dependency Injection to Manage a Singleton CustomerioClient instance

```java
public class ExampleModule extends AbstractModule {

    @Override
    protected void configure() {
        bind( CustomerioClient.class ).in( Singleton.class );
    }

    @Named( "customerioJsonWriter" )
    @Singleton
    @Provides
    public MessageBodyWriter<Object> jacksonMessageBodyProvdier( final Environment environment ) {
        return new JacksonMessageBodyProvider(environment.getObjectMapperFactory().build(), environment.getValidator());
    }

    @Named( "customerioHttpClient" )
    @Singleton
    @Provides
    public Client customerioHttpClient( final Environment environment, final WebhooksConfiguration configuration ) {
        final Client customerioHttpClient = new JerseyClientBuilder()
                .using( environment )
                .using( configuration.getCustomerioHttpClientConfiguration() )
                .build();
        return customerioHttpClient;
    }

}
```

#### Identify a Customer

Customers must be identified before they can be classified and emails can be sent.

[JavaDoc][1]  
[Customerio Documentation](http://customer.io/docs/api/rest.html#section-Creating_or_updating_customers)

```java
final String aerofsCustomerId = "eric+example@aerofs.com";
final String email = aerofsCustomerId;

// a customer with no attributes
customerioClient.identifyCustomer( aerofsCustomerId, new CustomerioCustomer( email, Optional.absent() ) );

final Map<String, Object> attributes = ImmutableMap.<String, Object> of(
        "first_name", user.getFirstName()
        "last_name", user.getLastName() );

// a customer with custom attributes
customerioClient.identifyCustomer( aerofsCustomerId, new CustomerioCustomer( email, Optional.of( attributes ) ) );
```

#### Delete a Customer

[JavaDoc][2]  
[Customerio Documentation](http://customer.io/docs/api/rest.html#section-Deleting_customers)

```java
final String aerofsCustomerId = "eric+example@aerofs.com";

customerioClient.deleteCustomer( aerofsCustomerId );
```

#### Track an event

Track an event associated with a customer.  An event can be any implicit or explicit action associated with the target AeroFS customer.  The Customerio dashboards can be used to configure what conditions need to be met before related emails are sent.

[JavaDoc][3]  
[Customerio Documentation](http://customer.io/docs/api/rest.html#section-Track_a_custom_event)

```java
final String aerofsCustomerId = "eric+example@aerofs.com";
final String eventId = "invoice.payment_succeeded";
final Invoice invoice = magic.getInvoice();

customerioClient.trackEvent( aerofsCustomerId, new CustomerioEvent<>( eventId, Optional.of( invoice ) ) );
```

[0]: https://github.arrowfs.org/pages/erics/customerio/javadoc/com/aerofs/customerio/CustomerioClient.html#CustomerioClient(com.sun.jersey.api.client.Client,%20javax.ws.rs.ext.MessageBodyWriter)
[1]: https://github.arrowfs.org/pages/erics/customerio/javadoc/com/aerofs/customerio/CustomerioClient.html#identifyCustomer(java.lang.String,%20com.aerofs.customerio.CustomerioCustomer)
[2]: https://github.arrowfs.org/pages/erics/customerio/javadoc/com/aerofs/customerio/CustomerioClient.html#deleteCustomer(java.lang.String)
[3]: https://github.arrowfs.org/pages/erics/customerio/javadoc/com/aerofs/customerio/CustomerioClient.html#trackEvent(java.lang.String,%20com.aerofs.customerio.CustomerioEvent)
