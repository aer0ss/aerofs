package com.aerofs.webhooks.entities;

import static org.fest.assertions.api.Assertions.assertThat;

import java.util.Set;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.db.DatabaseConfiguration;
import com.yammer.dropwizard.hibernate.SessionFactoryFactory;

@RunWith( MockitoJUnitRunner.class )
public class OrganizationDaoTest {

    private final SessionFactoryFactory factory = new SessionFactoryFactory();
    @Mock private Environment environment;
    private DatabaseConfiguration configuration = new DatabaseConfiguration();
    private SessionFactory sessionFactory;

    @Before
    public void setup() throws Exception {
        configuration.setUrl( "jdbc:mysql://localhost:3306/aerofs_sp" );
        configuration.setUser( "aerofs_sp" );
        configuration.setPassword( "password" );
        configuration.setDriverClass( "com.mysql.jdbc.Driver" );
        configuration.setValidationQuery( "SELECT 1;" );
        configuration.setProperties( ImmutableMap.of( "hibernate.dialect", "org.hibernate.dialect.MySQLDialect",
            "hibernate.current_session_context_class", "thread" ) );
    }

    @After
    public void tearDown() throws Exception {
        if ( sessionFactory != null ) {
            sessionFactory.close();
        }
    }

    @Ignore
    @Test
    public void test() throws Exception {
        makeSessionFactory();

        final Session session = sessionFactory.getCurrentSession();
        final Transaction transaction = session.beginTransaction();

        final OrganizationDao organizationDao = new OrganizationDao( sessionFactory );
        final Organization organization = organizationDao.findByStripeCustomerId( "abc123" );
        final Set<User> administrators = organization.getAdministrators();
        assertThat( administrators.size() ).isEqualTo( 2 );

        transaction.commit();
        sessionFactory.close();
    }

    private void makeSessionFactory() throws ClassNotFoundException {
        this.sessionFactory = factory.build( environment, configuration,
            ImmutableList.<Class<?>> of( Organization.class, User.class ) );
    }
}
