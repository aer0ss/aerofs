/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.proto.Diagnostics.TransferDiagnostics;
import com.aerofs.proto.Diagnostics.TransportTransfer;
import com.google.common.collect.Maps;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public final class TransferStatisticsManager
{
    private static class TransferStatistics
    {
        long _bytesTransferred = 0;
        long _bytesErrored = 0;
    }

    private final Map<String, TransferStatistics> _statsMap = Maps.newHashMap();

    public TransferStatisticsManager()
    {
        for (TransportType transportType : TransportType.values()) {
            _statsMap.put(transportType.getId(), new TransferStatistics());
        }
    }

    public synchronized void markTransferred(String transportId, long transferred)
    {
        checkArgument(transferred >= 0, "transferred:%s", transferred);
        checkArgument(_statsMap.containsKey(transportId), "unknown transport:%s", transportId);

        _statsMap.get(transportId)._bytesTransferred += transferred;
    }

    public synchronized void markErrored(String transportId, long errored)
    {
        checkArgument(errored >= 0, "errored:%s", errored);
        checkArgument(_statsMap.containsKey(transportId), "unknown transport:%s", transportId);

        _statsMap.get(transportId)._bytesErrored += errored;
    }

    public synchronized TransferDiagnostics getAndReset()
    {
        long totalBytesTransferred = 0;
        long totalBytesErrored = 0;

        // top-level message
        TransferDiagnostics.Builder allTransfersBuilder = TransferDiagnostics.newBuilder();

        // iterate over all the transports
        for (Map.Entry<String, TransferStatistics> entry : _statsMap.entrySet()) {
            String transportId = entry.getKey();
            TransferStatistics statistics = entry.getValue();

            // construct the information for _this_ transport
            TransportTransfer.Builder transferBuilder = TransportTransfer.newBuilder();
            transferBuilder.setTransportId(transportId);
            transferBuilder.setBytesTransferred(statistics._bytesTransferred);
            transferBuilder.setBytesErrored(statistics._bytesErrored);

            // update the global info
            totalBytesTransferred += statistics._bytesTransferred;
            totalBytesErrored += statistics._bytesErrored;

            // reset the stats info
            // NOTE: this _relies_ on the fact that the iterator is returning
            // the actual object and not a copy of the object
            statistics._bytesTransferred = 0;
            statistics._bytesErrored = 0;

            // add this to the top-level message
            allTransfersBuilder.addTransfer(transferBuilder);
        }

        // add the global info
        allTransfersBuilder.setTotalBytesTransferred(totalBytesTransferred);
        allTransfersBuilder.setTotalBytesErrored(totalBytesErrored);

        return allTransfersBuilder.build();
    }
}
