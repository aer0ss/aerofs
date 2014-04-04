package com.aerofs.webhooks.entities;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Set;

import javax.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import com.yammer.dropwizard.hibernate.AbstractDAO;

public class UserDao extends AbstractDAO<User> {

    @Inject
    public UserDao( final SessionFactory sessionFactory ) {
        super( sessionFactory );
    }

    public Set<User> findOrganizationAdministrators( final int organizationId ) {
        final Criteria criteria = criteria()
                .add( Restrictions.eq( "organization.id", organizationId ) )
                .add( Restrictions.eq( "administrator", true ) );

        return newHashSet( list( criteria ) );
    }

}
