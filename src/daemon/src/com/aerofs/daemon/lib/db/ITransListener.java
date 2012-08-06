package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;

public interface ITransListener
{
    /**
     * The reason that this method passes the {@link Trans} object is listeners usually implement
     * this method to flush data into database, and flushing requires a transaction. The other
     * methods in this class shouldn't require a transaction and are called after the transaction
     * finishes.
     */
    void committing_(Trans t) throws SQLException;

    void committed_();

    void aborted_();
}
