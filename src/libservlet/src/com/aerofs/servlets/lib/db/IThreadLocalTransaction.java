/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

public interface IThreadLocalTransaction<T extends Throwable>
{
    /**
     * Begins a transaction on this connector. Asserts that there isn't already an ongoing
     * transaction.
     */
    public void begin() throws T;

    /**
     * Commits the transaction currently in progress. Asserts that there is an ongoing transaction
     * to commit.
     */
    public void commit() throws T;

    /**
     * @return true if and only if it is called after begin() and before commit() or rollback()
     */
    public boolean isInTransaction();

    /**
     * Cleans up current connections and puts the connector back into a consistent state. Should be
     * run from the encodeError method of *Service classes (see SyncStatService.java for an example)
     * and in tests when an exception is thrown.
     */
    public void handleException();

    /**
     * Rolls back the current transaction. Asserts that there is an ongoing transaction to rollback.
     */
    public void rollback() throws T;

    /**
     * Must be called after completing a request to ensure that there are no open transactions.
     * Should be called from the servlet after requests complete and in @After methods in tests.
     */
    public void cleanUp() throws T;
}
