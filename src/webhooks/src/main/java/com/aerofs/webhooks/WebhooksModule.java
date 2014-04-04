package com.aerofs.webhooks;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.ext.MessageBodyWriter;

import org.hibernate.SessionFactory;

import com.aerofs.customerio.CustomerioClient;
import com.aerofs.webhooks.core.stripe.ProcessContext;
import com.aerofs.webhooks.entities.Organization;
import com.aerofs.webhooks.entities.OrganizationDao;
import com.aerofs.webhooks.entities.User;
import com.aerofs.webhooks.entities.UserDao;
import com.aerofs.webhooks.resource.StripeResource;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.sun.jersey.api.client.Client;
import com.yammer.dropwizard.client.JerseyClientBuilder;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.db.DatabaseConfiguration;
import com.yammer.dropwizard.hibernate.HibernateBundle;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;

public class WebhooksModule extends AbstractModule {

    @Override
    protected void configure() {
        bind( OrganizationDao.class );
        bind( UserDao.class );
        bind( CustomerioClient.class ).in( Singleton.class );
        bind( ProcessContext.class );
        bind( StripeResource.class );
    }

    @Named( "customerioJsonWriter" )
    @Singleton
    @Provides
    public MessageBodyWriter<Object> jacksonMessageBodyProvdier( final Environment environment ) {
        return new JacksonMessageBodyProvider(environment.getObjectMapperFactory().build(), environment.getValidator());
    }

    @Singleton
    @Provides
    public HibernateBundle<WebhooksConfiguration> hibernateBundle() {
        final Class<?> [] entities = new Class<?> [] { User.class, Organization.class };
        return new HibernateBundle<WebhooksConfiguration>( entities ) {
            @Override
            public DatabaseConfiguration getDatabaseConfiguration( final WebhooksConfiguration configuration ) {
                return configuration.getDatabaseConfiguration();
            }
        };
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

    @Singleton
    @Provides
    public SessionFactory sessionFactory( final HibernateBundle<WebhooksConfiguration> hibernateBundle ) {
        return hibernateBundle.getSessionFactory();
    }

}
