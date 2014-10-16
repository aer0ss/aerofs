/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.Loggers;
import com.google.inject.Inject;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ResourceMethodDispatchAdapter;
import com.sun.jersey.spi.container.ResourceMethodDispatchProvider;
import com.sun.jersey.spi.dispatch.RequestDispatcher;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.ext.Provider;

@Provider
public class TransactionalWrapper implements ResourceMethodDispatchAdapter
{
    private final static Logger l = Loggers.getLogger(TransactionalWrapper.class);

    private final SessionFactory _sessionFactory;

    @Inject
    public TransactionalWrapper(SessionFactory sessionFactory)
    {
        _sessionFactory = sessionFactory;
    }

    @Override
    public ResourceMethodDispatchProvider adapt(ResourceMethodDispatchProvider provider)
    {
        return am -> {
            RequestDispatcher d = provider.create(am);
            final Transactional t = transactional(am);
            if (t == null) return d;
            final boolean readOnly = t.readOnly();
            return (o, httpContext) -> {
                l.debug("hibernate trans {}", readOnly);
                Session session = _sessionFactory.openSession();
                try {
                    session.setDefaultReadOnly(readOnly);
                    ManagedSessionContext.bind(session);
                    session.beginTransaction();
                    try {
                        d.dispatch(o, httpContext);
                        doCommit(session);
                    } catch (RuntimeException e) {
                        l.warn("Txn failure {}", e);
                        doRollback(session);
                        throw e;
                    }
                } finally {
                    session.close();
                    ManagedSessionContext.unbind(_sessionFactory);
                }
            };
        };
    }

    private static @Nullable Transactional transactional(AbstractMethod am)
    {
        Transactional t = am.getAnnotation(Transactional.class);
        if (t == null) t = am.getMethod().getDeclaringClass().getAnnotation(Transactional.class);
        return t;
    }

    private static void doRollback(Session session)
    {
        final Transaction txn = session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.rollback();
        }
    }

    private static void doCommit(Session session)
    {
        final Transaction txn = session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.commit();
        }
    }
}
