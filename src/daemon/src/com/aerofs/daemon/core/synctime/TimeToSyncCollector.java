/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.collector.RemoteUpdates;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.net.device.IDevicePresenceListener;
import com.aerofs.daemon.core.protocol.Download.Factory;
import com.aerofs.daemon.core.protocol.GetVersReply;
import com.aerofs.daemon.core.protocol.IDownloadCompletionListener;
import com.aerofs.daemon.core.protocol.IPullUpdatesListener;
import com.aerofs.daemon.core.protocol.IPushUpdatesListener;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class is a plug-in that listens to a number of events around the core to record the
 * time-to-sync of objects. For each remote device, it locally records (in a 3D Histogram) the time
 * it took to locally download each object.
 *
 * Sync time is measured according to one of two baselines:
 *  - A checkpoint is associated with a device d, recording either when d came online, or the last
 *    time a pull-version request from d indicated there were no updates available on d (i.e. there
 *    were no Collector Bloom filters from d).
 *  - When a push version update is received from device d for object o, that time is recorded.
 *    These updates grow with the number of objects, so this map of times could grow very quickly
 *    thus must be cleared out when too large. Clearing update times means the time-to-sync will be
 *    overestimated (as we default to the last recorded checkpoint), so there is a
 *    balance between accuracy and space consumption.
 *
 * For more on the motivation and thought process behind time-to-sync, see time_to_sync.txt
 *
 * TODO (MJ) perhaps the checkpointing could be done earlier, perhaps adding an event when a Bloom
 * filter is deleted from the Collector.
 */
class TimeToSyncCollector implements
        IDevicePresenceListener, IPushUpdatesListener, IPullUpdatesListener,
        IDownloadCompletionListener
{
    // Invariant: there should be no _updateTimes[did][:] with time (Long)
    //            before _deviceCheckoutPoint[did]
    private final Map<DID, Long> _deviceCheckPoint;
    private final Table<DID, SOCID, Long> _updateTimes;

    private final RemoteUpdates _ru;
    private final TimeToSyncHistogram _histogram;
    private final TimeRetriever _time;

    private static final Logger l = Loggers.getLogger(TimeToSyncCollector.class);

    @Inject
    TimeToSyncCollector(TimeToSyncHistogram histogram,
            TimeRetriever timeRetriever, RemoteUpdates ru, DevicePresence devicePresence,
            NewUpdates newUpdates, GetVersReply getVersReply, Factory downloadFactory)
    {
        _histogram = histogram;
        _time = timeRetriever;
        _ru = ru;

        _deviceCheckPoint = Maps.newHashMap();
        _updateTimes = new MemoryLimitedLinkedHashMapBasedTable<DID, SOCID, Long> (
                Params.UPDATE_TIMES_TABLE_SIZE_UPPER_BOUND);

        addListeners_(devicePresence, newUpdates, getVersReply, downloadFactory);
    }

    private void addListeners_(DevicePresence devicePresence, NewUpdates newUpdates,
            GetVersReply getVersReply, Factory downloadFactory)
    {
        devicePresence.addListener_(this);
        newUpdates.addListener_(this);
        getVersReply.addListener_(this);
        downloadFactory.addListener_(this);
    }

    @Override
    public void deviceOnline_(DID did)
    {
        l.debug("{} online", did);
        Long prev = _deviceCheckPoint.put(did, _time.currentTimeMillis());

        // Since this device just came online, there should be no previously recorded checkpoint
        // nor update times
        checkState(prev == null, did);
        checkState(_updateTimes.row(did).isEmpty(), did);
    }

    @Override
    public void deviceOffline_(DID did)
    {
        l.debug("{} offline", did);
        _updateTimes.row(did).clear();
        _updateTimes.rowMap().remove(did);
        checkNotNull(_deviceCheckPoint.remove(did), did);
    }

    @Override
    public void receivedPushUpdate_(SOCID socid, DID didFrom)
    {
        _updateTimes.put(didFrom, socid, _time.currentTimeMillis());
    }

    @Override
    public void receivedPullUpdateFrom_(DID did) throws SQLException
    {
        // The checkoutpoint map should already have did
        // (i.e. deviceOnline should have preceded this call once)
        checkState(_deviceCheckPoint.containsKey(did), did);

        if (!_ru.deviceHasUpdates_(did)) {
            l.debug("move checkpoint for {}", did);

            // There are no updates to download from did, so move the checkpoint forward
            _deviceCheckPoint.put(did, _time.currentTimeMillis());
            _updateTimes.row(did).clear();
            _updateTimes.rowMap().remove(did);
        }
    }

    /**
     * A partially successful download implies that there could still be KMLs. Regardless,
     * we must record this time because orphaned KMLs are a
     * possibility and we would otherwise never record sync time for orphaned KMLs
     */
    @Override
    public void onPartialDownloadSuccess_(SOCID socid, DID didFrom)
    {
        Long updateTime = _updateTimes.get(didFrom, socid);

        long syncTime = _time.currentTimeMillis()
                - (updateTime != null ? updateTime : _deviceCheckPoint.get(didFrom));

        // Delete the update timestamp
        // - timestamp was created because didFrom sent a push notification, so that
        //   version of socid should be local to didFrom, so it should be *at least* what was
        //   locally downloaded here.
        // - didFrom may have expelled the object shortly thereafter, thus we didn't
        //   download it, but we'll get another push update (presumably) when socid is
        //   re-admitted.
        // TODO (MJ) if socid was expelled on didFrom, we should have received an
        //   exception and aborted the download
        if (updateTime != null) _updateTimes.remove(didFrom, socid);

        _histogram.update_(didFrom, socid.oid(), new TimeToSync(syncTime));
    }

    @Override
    public void onDownloadSuccess_(SOCID socid, DID from)
    {
        // TODO (MJ) another way to reduce space of _updateTimes is to remove (from, socid)
        // whenever the KMLs for socid are reduced to zero. i.e. all received push updates have
        // been resolved. Future downloads of socid would be caused by new push updates, thus
        // overwriting the existing values, or by pull update, but the checkpoints should take
        // care of the latter.
        //
        // assert that no KMLs exist for socid
        // _updateTimes.column(socid).clear();
    }

    @Override
    public void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e) { }

    @Override
    public void onGeneralError_(SOCID socid, Exception e) { }

    /**
     * A wrapper for System.currentTimeMillis() to help with JUnit testing
     */
    static class TimeRetriever
    {
        long currentTimeMillis()
        {
            return System.currentTimeMillis();
        }
    }
}
