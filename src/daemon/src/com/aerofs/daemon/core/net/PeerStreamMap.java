/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IStream;
import com.aerofs.daemon.tng.StreamMap;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.proto.Transport;
import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread safe mapping of <DID, StreamID> --> IImmutableStream implementation
 */
public class PeerStreamMap<Stream extends IStream>
{
    private final Map<DID, StreamMap<Stream>> _map;

    public static <Stream extends IStream> PeerStreamMap<Stream> create()
    {
        return new PeerStreamMap<Stream>();
    }

    public PeerStreamMap()
    {
        _map = new HashMap<DID, StreamMap<Stream>>();
    }

    public synchronized void addStream(DID did, Stream stream)
            throws ExStreamAlreadyExists
    {
        StreamMap<Stream> map = _map.get(did);
        if (map == null) {
            map = new StreamMap<Stream>();
            _map.put(did, map);
        }

        map.add(stream);
    }

    public synchronized Stream removeStream(DID did, StreamID streamId)
    {
        StreamMap<Stream> map = _map.get(did);
        if (map == null) return null;

        return map.remove(streamId);
    }

    public synchronized Stream getStream(DID did, StreamID streamId)
            throws ExStreamInvalid
    {
        StreamMap<Stream> map = _map.get(did);
        if (map == null) throw new ExStreamInvalid(streamId,
                Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND);

        return map.get(streamId);
    }

    @BindingAnnotation
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IncomingStreamMap
    {
    }

    @BindingAnnotation
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface OutgoingStreamMap
    {
    }
}
