/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.throttling;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.net.IIncomingStreamChunkListener;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.ex.ExNotFound;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Map;

/**
 * This class is notified when chunks are added and removed from their respective
 * IncomingStream chunk queues. By keeping a count of the number of chunks
 * currently present in a queue, we can maintain an estimate on the total amount
 * of memory each device is using.
 *
 * To prevent Out Of Memory situations (where the Core processes chunks much slower
 * than they are received), we tell the LimitMonitor to pause transfers for a device
 * that is determined to be using too much of our memory.
 *
 * Once that device lowers its memory usage (chunks are processed faster than they
 * arrive), we tell the LimitMonitor to resume transfers for the previously offending
 * device.
 */
public class IncomingStreamsThrottler implements IIncomingStreamChunkListener
{
    private static final Logger l = Loggers.getLogger(IncomingStreamsThrottler.class);

    /**
     * Keeps track of chunk counts for the whole device and for individual
     * streams of that device.
     */
    private static class DeviceStreamsInfo
    {
        private final Map<StreamID, Integer> _perStreamChunkCount = Maps.newHashMap();
        private int _totalChunkCount;
        public boolean _paused;

        public void increment_(StreamID streamID)
        {
            Integer count = _perStreamChunkCount.get(streamID);
            if (count == null) {
                count = 0;
            }
            _perStreamChunkCount.put(streamID, count + 1);
            _totalChunkCount++;
        }

        public void decrement_(StreamID streamID)
        {
            Integer count = _perStreamChunkCount.get(streamID);
            assert count != null;
            assert count > 0;
            assert _totalChunkCount > 0;

            count--;
            _totalChunkCount--;
            if (count == 0) {
                _perStreamChunkCount.remove(streamID);
            } else {
                _perStreamChunkCount.put(streamID, count);
            }
        }

        public void invalidate_(StreamID streamID)
        {
            Integer count = _perStreamChunkCount.get(streamID);
            if (count == null) {
                return;
            }

            _totalChunkCount -= count;
            _perStreamChunkCount.remove(streamID);
        }

        public int totalChunkCount()
        {
            return _totalChunkCount;
        }
    }

    private final int _lowWatermark;
    private final int _highWatermark;
    private final Map<DID, DeviceStreamsInfo> _deviceMap = Maps.newHashMap();

    private LimitMonitor _limitMonitor;

    @Inject
    public IncomingStreamsThrottler(CfgDatabase db, Metrics metrics, IncomingStreams iss)
    {
        _lowWatermark = db.getInt(Key.LOW_CHUNK_WATERMARK);
        _highWatermark = db.getInt(Key.HIGH_CHUNK_WATERMARK);
        iss.addListener_(this);

        l.info("watermark lo ~" + (metrics.getMaxUnicastSize_() * _lowWatermark) + " hi ~" +
                (metrics.getMaxUnicastSize_() * _highWatermark));
    }

    /**
     * Work around the fact that LimitMonitor is created using a factory (it must be due
     * to dependencies) and so we can not inject LimitMonitor. If we inject the factory,
     * we get a different instance which is undesired.
     *
     * @param limitMonitor The LimitMonitor from the UnicastInputOutputStack
     */
    public void setLimitMonitor_(LimitMonitor limitMonitor)
    {
        assert _limitMonitor == null;
        _limitMonitor = limitMonitor;
    }

    @Override
    public void onChunkReceived_(DID did, StreamID streamID)
    {
        assert _limitMonitor != null;

        DeviceStreamsInfo deviceUsage = _deviceMap.get(did);
        if (deviceUsage == null) {
            deviceUsage = new DeviceStreamsInfo();
            _deviceMap.put(did, deviceUsage);
        }

        deviceUsage.increment_(streamID);

        if (!deviceUsage._paused) {
            // The device isn't paused, so we should check if it passed the high watermark.
            // If it was paused, that means we already passed the high watermark and should
            // only be interested if the total chunk count drops below the low watermark.
            if (deviceUsage.totalChunkCount() >= _highWatermark) {
                deviceUsage._paused = true;
                try {
                    _limitMonitor.pauseDevice_(did);
                } catch (ExNotFound e) {
                    l.error(Util.e(e));
                }
            }
        }
    }

    @Override
    public void onChunkProcessed_(DID did, StreamID streamID)
    {
        assert _limitMonitor != null;

        DeviceStreamsInfo deviceUsage = _deviceMap.get(did);
        assert deviceUsage != null;

        deviceUsage.decrement_(streamID);

        processDecrementedCount_(did, deviceUsage);
    }

    @Override
    public void onStreamInvalidated_(DID did, StreamID streamID)
    {
        assert _limitMonitor != null;

        DeviceStreamsInfo deviceUsage = _deviceMap.get(did);
        if (deviceUsage == null) {
            return;
        }

        deviceUsage.invalidate_(streamID);

        processDecrementedCount_(did, deviceUsage);
    }

    private void processDecrementedCount_(DID did, DeviceStreamsInfo deviceUsage)
    {
        if (deviceUsage._paused) {
            // If the device is paused, we need to see if the total chunk count drops below
            // the low watermark. If it does, we resume the device.
            if (deviceUsage.totalChunkCount() <= _lowWatermark) {
                deviceUsage._paused = false;
                try {
                    _limitMonitor.resumeDevice_(did);
                } catch (ExNotFound e) {
                    l.error(Util.e(e));
                }
            }
        }

        if (deviceUsage.totalChunkCount() == 0) {
            assert !deviceUsage._paused;
            _deviceMap.remove(did);
        }
    }
}
