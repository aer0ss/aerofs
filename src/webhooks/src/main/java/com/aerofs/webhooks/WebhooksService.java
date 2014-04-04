package com.aerofs.webhooks;

import com.aerofs.webhooks.resource.StripeResource;
import com.google.inject.Key;
import com.hubspot.dropwizard.guice.GuiceBundle;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.hibernate.HibernateBundle;

public class WebhooksService extends Service<WebhooksConfiguration> {

    public static void main( final String [] args ) throws Exception {
        new WebhooksService().run( args );
    }

    @Override
    public void initialize( final Bootstrap<WebhooksConfiguration> bootstrap ) {
        final GuiceBundle<WebhooksConfiguration> guiceBundle = GuiceBundle.<WebhooksConfiguration> newBuilder()
                .setConfigClass( WebhooksConfiguration.class )
                .addModule( new WebhooksModule() )
                .enableAutoConfig( getClass().getPackage().getName() )
                .build();

        // needs to happen before hibernate bundle so juice is able to inject the HibernateBundle instance
        bootstrap.addBundle( guiceBundle );
        // bug in dropwizard requires this call to be made, https://github.com/codahale/dropwizard/issues/237
        guiceBundle.initialize( bootstrap );

        final HibernateBundle<WebhooksConfiguration> hibernateBundle = guiceBundle.getInjector()
                .getInstance( new Key<HibernateBundle<WebhooksConfiguration>>() {} );

        bootstrap.addBundle( hibernateBundle );
    }

    @Override
    public void run( final WebhooksConfiguration configuration, final Environment environment )
            throws Exception {
        environment.addResource( StripeResource.class );
    }

}
