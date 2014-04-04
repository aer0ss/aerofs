package com.aerofs.webhooks.entities;

import javax.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import com.yammer.dropwizard.hibernate.AbstractDAO;

public class OrganizationDao extends AbstractDAO<Organization> {

    @Inject
    public OrganizationDao( final SessionFactory sessionFactory ) {
        super( sessionFactory );
    }

    public Organization findByStripeCustomerId( final String stripeCustomerId ) {
        final Criteria criteria = criteria();
        criteria.add( Restrictions.eq( "stripeCustomerId", stripeCustomerId ) );

        final Organization result = list( criteria ).get( 0 );
        return result;
    }

}
