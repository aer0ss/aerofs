/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND;

public final class StreamMap<Stream extends IStream> implements IStreamMap<Stream>
{
    private static final Logger l = Util.l(StreamMap.class);

    private final ConcurrentMap<StreamID, Stream> _streams = makeMap();

    private static <K, V> ConcurrentMap<K, V> makeMap()
    {
        return new ConcurrentHashMap<K, V>();
    }

    @Override
    public void add(final Stream stream)
            throws ExStreamAlreadyExists
    {
        l.debug("add:" + stream);

        Stream prev = _streams.put(stream.getStreamId_(), stream);
        if (prev != null) throw new ExStreamAlreadyExists(stream.getStreamId_());
    }

    @Override
    public Stream remove(StreamID strmid)
    {
        l.debug("rem stream id:" + strmid);

        return _streams.remove(strmid);
    }

    @Override
    public Stream get(StreamID streamid)
            throws ExStreamInvalid
    {
        Stream stream = _streams.get(streamid);
        if (stream == null) throw new ExStreamInvalid(streamid, STREAM_NOT_FOUND);
        return stream;
    }
}