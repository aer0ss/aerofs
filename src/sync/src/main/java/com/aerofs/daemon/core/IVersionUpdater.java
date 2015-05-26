package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;

public interface IVersionUpdater
{
    @FunctionalInterface
    public interface IListener
    {
        public void updated_(SOCKID k, Trans t);
    }

    public void addListener_(IListener l);
    public void update_(SOCKID k, @Nonnull Trans t) throws SQLException, IOException;
}