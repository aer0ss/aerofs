package com.aerofs.lib.fsi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.proto.Fsi.PBFSICall;
import com.aerofs.proto.Fsi.PBFSIReply;

/**
 * This class provides an FSI connection pool
 *
 * usage:
 *      fsi = FSIClient.newConnection();
 *      try {
 *          ...
 *      } finally {
 *          fsi.close_();
 *      }
 */
public class FSIClient {
    private static final Logger l = Util.l(FSIClient.class);

    private FSIClientImpl _c;
    private final InetAddress _addr;
    private final int _port;
    private final boolean _pooled;

    // protected by synchronized (s_pool) {}
    static private final LinkedList<FSIClient> s_pool =
        new LinkedList<FSIClient>();

    public static FSIClient newConnection()
    {
        return new FSIClient(C.LOCALHOST_ADDR, Cfg.port(PortType.FSI), false);

        /*
        synchronized (s_pool) {
            if (s_pool.isEmpty()) {
                return new FSIClient(C.LOCALHOST_ADDR, Cfg.port(PortType.FSI), true);
            } else {
                // remove last for better locality
                return s_pool.removeLast();
            }
        }
        */
    }

    /**
     * allocates a non-pooled connection
     */
    public FSIClient(InetAddress addr, int port)
    {
        this(addr, port, false);
    }

    private FSIClient(InetAddress addr, int port, boolean pooled)
    {
        _addr = addr;
        _port = port;
        _pooled = pooled;
    }

    private FSIClientImpl get_() throws IOException
    {
        if (_c == null) _c = new FSIClientImpl(_addr, _port);
        return _c;
    }

    /**
     * if the object is a pooled connection, return the object to the pool.
     * otherwise close the connection
     */
    public void close_()
    {
        if (_pooled) {
            synchronized (s_pool) {
                if (s_pool.size() < Param.FSICLIENT_POOL_SIZE) {
                    s_pool.addLast(this);
                    return;
                }
            }
        }

        if (_c != null) {
            _c.close_();
            _c = null;
        }
    }

    public PBFSIReply rpc_(PBFSICall.Builder call) throws Exception
    {
        return rpc_(call.build());
    }

    public PBFSIReply rpc_(PBFSICall call) throws Exception
    {
        try {
            return get_().rpc_(call);
        } catch (IOException e) {
            l.warn("discard fsi: " + e);
            _c = null;
            throw e;
        }
    }

    public PBFSIReply rpc_(PBFSICall.Builder call, int timeout) throws Exception
    {
        return rpc_(call.build(), timeout);
    }

    public PBFSIReply rpc_(PBFSICall call, int timeout) throws Exception
    {
        try {
            return get_().rpc_(call, timeout);
        } catch (IOException e) {
            l.warn("discard fsi: " + e);
            _c = null;
            throw e;
        }
    }

    public void send_(PBFSICall call) throws IOException
    {
        try {
            get_().send_(call);
        } catch (IOException e) {
            l.warn("clear fsi cache: " + e);
            _c = null;
            throw e;
        }
    }

    @Override
    protected void finalize()
    {
        // fsi that are in the pool will not be finalized

        if (_c != null) {
            l.error("!!!!! leaking " + FSIClient.class.getName() + " !!!!");
            close_();
        }
    }
}
