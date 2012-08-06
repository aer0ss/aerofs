package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;

public abstract class AbstractTransListener implements ITransListener
{
    @Override
    public void committing_(Trans t) throws SQLException
    {
    }

    @Override
    public void committed_()
    {
    }

    @Override
    public void aborted_()
    {
    }
}
