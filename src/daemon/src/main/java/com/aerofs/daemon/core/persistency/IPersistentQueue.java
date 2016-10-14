/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.persistency;

import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;

import java.sql.SQLException;

/**
 * This interface defines a FIFO queue (which is expected to be backed by the core DB and therefore
 * persistent) and a method to process the items from that queue.
 *
 * See {@link PersistentQueueDriver} for details
 */
public interface IPersistentQueue<I, O>
{
    /**
     * Append a command at the tail of the queue
     * NB: Called with core lock held
     */
    void enqueue_(I payload, Trans t) throws SQLException;

    /**
     * @return a (batch of) item(s) read from the head of queue
     * NB: Called with core lock held
     */
    O front_() throws SQLException;

    /**
     * Process a (batch of) item(s)
     * @return true if processing was successful, false if a retry is needed
     * NB: Called with core lock held, use the passed Token to release it as needed
     */
    boolean process_(O payload, Token tk) throws Exception;

    /**
     * Remove a successfully processed (batch of) item(s) from the head of the queue
     */
    void dequeue_(O payload, Trans t) throws SQLException;
}
