/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.Loggers;
import com.google.common.collect.ImmutableSet;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;

/**
 * A netty handler that wraps upstream handlers in a Hibernate transaction
 */
public class TransactionalWrapper extends SimpleChannelUpstreamHandler
{
    private final static Logger l = Loggers.getLogger(TransactionalWrapper.class);

    private final SessionFactory _sessionFactory;
    private static Logger _l = Loggers.getLogger(TransactionalWrapper.class);

    // list of path that need transactions, either read-write or read-only
    private final ImmutableSet<String> _rw;
    private final ImmutableSet<String> _ro;

    /**
     * Create a transactional filter with an explicity readonly setting.
     */
    public TransactionalWrapper(SessionFactory sessionFactory,
            ImmutableSet<String> rw, ImmutableSet<String> ro)
    {
        _sessionFactory = sessionFactory;
        _rw = rw;
        _ro = ro;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
    {
        HttpRequest request = (HttpRequest)me.getMessage();

        String path = new QueryStringDecoder(request.getUri()).getPath();
        int idx = path.indexOf('/', 1);
        String base = path.substring(0, idx != -1 ? idx : path.length());
        boolean rw = _rw.contains(base);
        if (rw || _ro.contains(base)) {
            wrapInTransaction(ctx, me, !rw);
        } else {
            ctx.sendUpstream(me);
        }
    }

    private void wrapInTransaction(ChannelHandlerContext ctx, MessageEvent me, boolean readOnly)
    {
        l.debug("hibernate trans {}", readOnly);
        final Session session = _sessionFactory.openSession();

        try {
            session.setDefaultReadOnly(readOnly);
            ManagedSessionContext.bind(session);
            session.beginTransaction();

            try {
                ctx.sendUpstream(me);
                doCommit(session);
            } catch (Exception e) {
                _l.warn("Txn failure {}", e);
                doRollback(session);
                throw new RuntimeException(e);
            }
        } finally {
            session.close();
            ManagedSessionContext.unbind(_sessionFactory);
        }
    }

    private void doRollback(Session session)
    {
        final Transaction txn = session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.rollback();
        }
    }

    private void doCommit(Session session)
    {
        final Transaction txn = session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.commit();
        }
    }
}
