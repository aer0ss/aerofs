/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.providers;

import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.sparta.Transactional;
import com.sun.jersey.api.container.MappableContainerException;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.spi.container.ResourceMethodDispatchAdapter;
import com.sun.jersey.spi.container.ResourceMethodDispatchProvider;
import com.sun.jersey.spi.dispatch.RequestDispatcher;

import javax.inject.Inject;
import javax.ws.rs.ext.Provider;
import java.sql.SQLException;

/**
 * Wraps a JAX-RS resource method in an SQL transaction
 */
@Provider
public class TransactionWrapper implements ResourceMethodDispatchAdapter
{
    private final SQLThreadLocalTransaction _sqlTrans;
    private final ThreadLocalSFNotifications _sfNotif;

    @Inject
    public TransactionWrapper(SQLThreadLocalTransaction sqlTrans, ThreadLocalSFNotifications sfNotif)
    {
        _sqlTrans = sqlTrans;
        _sfNotif = sfNotif;
    }

    @Override
    public ResourceMethodDispatchProvider adapt(ResourceMethodDispatchProvider provider)
    {
        return new TransactionalDispatchProvider(provider);
    }

    private class TransactionalDispatchProvider implements ResourceMethodDispatchProvider
    {
        private final ResourceMethodDispatchProvider _wrapped;

        public TransactionalDispatchProvider(ResourceMethodDispatchProvider wrapped)
        {
            _wrapped = wrapped;
        }

        @Override
        public RequestDispatcher create(AbstractResourceMethod method)
        {
            RequestDispatcher d = _wrapped.create(method);
            return (method.isAnnotationPresent(Transactional.class)
                    || method.getDeclaringResource().isAnnotationPresent(Transactional.class))
                    ? new TransactionalDispatcher(d)
                    : d;
        }
    }

    private class TransactionalDispatcher implements RequestDispatcher
    {
        private final RequestDispatcher _wrapped;

        public TransactionalDispatcher(RequestDispatcher wrapped)
        {
            _wrapped = wrapped;
        }

        @Override
        public void dispatch(Object o, HttpContext context)
        {
            boolean committed = false;
            try {
                _sqlTrans.begin();
                _wrapped.dispatch(o, context);
                _sqlTrans.commit();
                committed = true;
            } catch (SQLException e) {
                throw new MappableContainerException(e);
            } finally {
                _sfNotif.clear();
                if (!committed) _sqlTrans.handleException();
            }
        }
    }
}
