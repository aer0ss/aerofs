package com.aerofs.daemon.lib.db.trans;

import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.daemon.lib.db.trans.Trans.Factory;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.sql.SQLException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class TestTrans extends AbstractTest
{
    @Mock TransManager tm;
    @Mock IDBCW dbcw;

    @Mock ITransListener listener;

    Factory factTrans;

    Trans t;

    @Before
    public void setup()
    {
        factTrans = new Factory(dbcw);
        t = factTrans.create_(tm);
    }

    @Test
    public void shouldAbortWithFailingListeners()
            throws SQLException
    {
        commitWithFailingListeners();
        verify(dbcw).abort_();
        verify(dbcw, Mockito.never()).commit_();
    }

    @Test
    public void shouldMarkAsEndedWithFailingListeners()
            throws SQLException
    {
        commitWithFailingListeners();
        assertTrue(t.ended_());
    }

    @Test
    public void shouldNotifyListenersWhenAbortingDueToFailingListeners()
            throws SQLException
    {
        commitWithFailingListeners();
        verify(listener).aborted_();
    }

    private void commitWithFailingListeners()
            throws SQLException
    {
        doThrow(new SQLException("test")).when(listener).committing_(any(Trans.class));

        t.addListener_(listener);

        t.commit_();

        try {
            t.end_();
        } catch (SQLException e) {
        }
    }
}
