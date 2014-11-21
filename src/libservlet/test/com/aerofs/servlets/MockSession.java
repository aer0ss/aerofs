/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets;

import com.aerofs.base.C;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.session.ISession;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;

/**
 * A mock wrapper of HttpSession "user id" that ignores the concept of sessions. N.B. unlike with
 * the ThreadLocalHttpSessionUser, there are *not* thread-local user ids. The tests that use this
 * class should be single-threaded, so this isn't a problem. Intended use is in {@code
 * LocalSPServiceReactorCaller}.
 */
public class MockSession implements ISession
{
    private User _user;
    private Long basicAuthDate;
    private Long secondFactorAuthDate;
    private Long certificateAuthDate;

    static private long SESSION_LIFETIME = 30 * C.DAY;

    @Override
    public void setBasicAuthDate(long timestamp)
    {
        basicAuthDate = timestamp;
    }

    @Override
    public void setSecondFactorAuthDate(long timestamp)
    {
        secondFactorAuthDate = timestamp;
    }

    @Override
    public void setCertificateAuthDate(long timestamp)
    {
        certificateAuthDate = timestamp;
    }

    @Override
    public void dropSecondFactorAuthDate()
    {
        secondFactorAuthDate = null;
    }

    @Override
    public boolean isAuthenticated()
    {
        return getAuthenticatedProvenances().size() > 0;
    }

    @Override
    public ImmutableList<Provenance> getAuthenticatedProvenances()
    {
        ImmutableList.Builder<Provenance> builder = ImmutableList.builder();
        if (basicAuthDate != null &&
                (System.currentTimeMillis() - basicAuthDate) < SESSION_LIFETIME) {
            builder.add(Provenance.BASIC);
            if (secondFactorAuthDate != null &&
                    (System.currentTimeMillis() - secondFactorAuthDate) < SESSION_LIFETIME) {
                builder.add(Provenance.BASIC_PLUS_SECOND_FACTOR);
            }
        }
        if (certificateAuthDate != null &&
                (System.currentTimeMillis() - certificateAuthDate < SESSION_LIFETIME)) {
            builder.add(Provenance.CERTIFICATE);
        }
        return builder.build();
    }

    @Override
    public @Nonnull User getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup group)
            throws ExNotAuthenticated, ExSecondFactorRequired, ExNotFound, SQLException
    {
        if (_user == null) throw new ExNotAuthenticated();
        // Note: we avoid calling user.checkProvenance because that requires DB access and a trans
        if (!getAuthenticatedProvenances().contains(Provenance.BASIC)) throw new ExNotAuthenticated();
        return _user;
    }

    @Override
    public User getUserNullable()
    {
        return _user;
    }

    @Override
    public void setUser(@Nonnull User userID)
    {
        _user = userID;
        assertFalse(_user.id().getString().isEmpty());
    }

    @Override
    public void deauthorize()
    {
        _user = null;
        basicAuthDate = null;
        secondFactorAuthDate = null;
        certificateAuthDate = null;
    }

    @Override
    public String id()
    {
        // Doesn't matter.
        return "";
    }
}
