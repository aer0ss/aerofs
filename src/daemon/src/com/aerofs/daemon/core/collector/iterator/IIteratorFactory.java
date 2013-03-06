package com.aerofs.daemon.core.collector.iterator;

import java.sql.SQLException;

import com.aerofs.daemon.core.collector.CollectorSeq;

import javax.annotation.Nullable;

public interface IIteratorFactory
{
    /**
     * @param cs null to start a fresh iteration, non null to continue from the
     * specified cs
     */
    IIterator newIterator_(@Nullable CollectorSeq cs) throws SQLException;
}
