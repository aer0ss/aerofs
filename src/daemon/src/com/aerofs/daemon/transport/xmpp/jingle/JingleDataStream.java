/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.xmpp.XUtil;
import com.aerofs.j.StreamEvent;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.StreamInterface_EventSlot;
import com.aerofs.j.StreamResult;
import com.aerofs.j.StreamState;
import com.aerofs.j.j;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.proto.Transport.PBTPHeader;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;

import static com.aerofs.daemon.lib.DaemonParam.Jingle.QUEUE_LENGTH;

// A JingleDataStream object represents a bidirectional jingle stream
//
// N.B. all the methods of this class must be called within the signal thread.
//
public class JingleDataStream implements IProxyObjectContainer
{
    private static final Logger l = Loggers.getLogger(JingleDataStream.class);

    static interface IClosureListener
    {
        void closed_(JingleDataStream self);
    }

    static interface IConnectionListener
    {
        void connected_(JingleDataStream self);
    }

    private final IJingle ij;
    private final DID did;
    private final StreamInterface _streamInterface;
    private boolean _writable;
    private boolean _firstwrite = false;
    private long _bytesIn;

    private final PrioQueue<SendData> _q = new PrioQueue<SendData>(QUEUE_LENGTH);

    // each SendData is an atomic chunk of data and can't be preempted by other
    // data even if they are at higher priorities. therefore we have to remember
    // the current data entry in a place other than the queue head.
    private SendData _outCur;
    private int _outOff;    // the offset of the current byte array of _outCur

    private final byte[] _inHeader = new byte[XUtil.getHeaderLen()];
    private int _inHeaderOff;

    private byte[] _inPayload;
    private int _inPayloadOff;

    private final IClosureListener _closureListener;
    private final IConnectionListener _connectionListener;
    private final boolean _incoming;

    // keep a reference to prevent the slot being GC'ed (as there's no Java reference to this object otherwise)
    private final StreamInterface_EventSlot _slotEvent = new StreamInterface_EventSlot()
    {
        @Override
        public void onEvent(StreamInterface s, int event, int error)
        {
            try {
                l.info("stream e:" + event);
                onStreamEvent_(s, event, error);
            } catch (Exception e) {
                close_(e);
                _closureListener.closed_(JingleDataStream.this);
            }
        }
    };

    JingleDataStream(IJingle ij, StreamInterface streamInterface, DID did, boolean incoming, IClosureListener closureListener, IConnectionListener connectionListener)
    {
        this.ij = ij;
        this.did = did;
        this._streamInterface = streamInterface;
        this._closureListener = closureListener;
        this._connectionListener = connectionListener;
        this._incoming = incoming;

        _slotEvent.connect(_streamInterface);
        _writable = _streamInterface.GetState() == StreamState.SS_OPEN;
    }

    boolean isIncoming()
    {
        return _incoming;
    }

    DID did()
    {
        return did;
    }

    private void onStreamEvent_(StreamInterface streamInterface, int event, int error)
        throws Exception
    {
        if ((event & StreamEvent.SE_WRITE.swigValue()) != 0) {
            if (!_firstwrite) {
                _firstwrite = true;
                _connectionListener.connected_(JingleDataStream.this);
            }

            _writable = true;

            write_();
        }

        if ((event & StreamEvent.SE_READ.swigValue()) != 0)  {
            read_();
        }

        if ((event & StreamEvent.SE_CLOSE.swigValue()) != 0) {
            throw new ExJingle("recv SE_CLOSE error " + error);
        }
    }

    // calling this method will not trigger IClosureListener
    void close_(Exception e)
    {
        l.debug("close jds " + this + ": " + Util.e(e, ExJingle.class));

        _streamInterface.Close();

        // notify the core that the current outgoing packet failed to be sent
        if (_outCur != null && _outCur.waiter() != null) {
            _outCur.waiter().error(e);
        }

        // notify the core that the queued packets failed to be sent
        while (!_q.isEmpty_()) {
            SendData sd = _q.dequeue_();
            if (sd.waiter() != null) sd.waiter().error(e);
        }
    }

    void send_(byte[][] bss, Prio prio, IResultWaiter waiter)
    {
        SendData sd = new SendData(bss, waiter);

        try {
            if (_outCur == null) {
                _outCur = sd;
                if (_writable) write_();
            } else if (_q.isFull_()) {
                throw new ExNoResource("jingle q full");
            } else {
                _q.enqueue_(sd, prio);
            }
        } catch (Exception e) {
            l.warn("drop packet d:" + did());
            if (waiter != null) waiter.error(e);
        }
    }

    private void write_() throws ExJingle
    {
        assert _writable;

        int[] written = { 0 };
        int[] error = { 0 };

        // iterate through data entries
        while (_outCur != null) {

            // itereate through byte arrays in each data entry
            while (true) {
                byte[] cur = _outCur.current();
                if (cur == null) {
                    if (_outCur.waiter() != null) _outCur.waiter().okay();
                    break;
                }

                StreamResult res;

                res = j.WriteAll(_streamInterface, cur, _outOff,
                        cur.length - _outOff, written, error);

                _outOff += written[0];
                ij.addBytesTx(written[0]);
                logio(written[0], _outOff, cur.length, res, true);

                if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                    assert _outOff < cur.length;
                    throw new ExJingle("write returns " + res + " error " + error[0]);
                } else if (res == StreamResult.SR_BLOCK) {
                    assert _outOff < cur.length;
                    _writable = false;
                    return;
                }

                assert res == StreamResult.SR_SUCCESS;
                assert _outOff == cur.length;

                // move on to the next byte array
                _outCur.next();
                _outOff = 0;
            }

            // move on to the next data entry
            _outCur = _q.isEmpty_() ? null : _q.dequeue_();
        }
    }

    private void read_() throws Exception
    {
        int[] read = { 0 };
        int[] error = { 0 };
        int msgHdrLen = 0;
        int msgPldLen;
        StreamResult res;
        while (true) {

            ////////
            // read header

            if (_inHeaderOff != _inHeader.length) {
                assert _inHeaderOff < _inHeader.length;
                // read the header
                res = j.ReadAll(_streamInterface, _inHeader, _inHeaderOff,
                        _inHeader.length - _inHeaderOff, read, error);
                _inHeaderOff += read[0];
                logio(read[0], _inHeaderOff, _inHeader.length, res, false);
                msgHdrLen = read[0];
                ij.addBytesRx(read[0]);

                if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                    assert _inHeaderOff < _inHeader.length;
                    throw new ExJingle("read header returns " + res + " error "
                            + error[0]);
                } else if (res == StreamResult.SR_BLOCK) {
                    assert _inHeaderOff < _inHeader.length;
                    break;
                }

                assert res == StreamResult.SR_SUCCESS;
                assert _inHeaderOff == _inHeader.length;
            }


            ////////
            // read payload

            if (_inPayload == null) {
                assert _inPayloadOff == 0;
                int len = XUtil.readHeader(_inHeader);
                _inPayload = new byte[len];
            }

            assert _inPayloadOff < _inPayload.length;

            res = j.ReadAll(_streamInterface, _inPayload, _inPayloadOff,
                    _inPayload.length - _inPayloadOff, read, error);
            _inPayloadOff += read[0];
            logio(read[0], _inPayloadOff, _inPayload.length, res, false);
            msgPldLen = read[0];
            ij.addBytesRx(read[0]);
            _bytesIn += read[0];

            assert _inPayloadOff <= _inPayload.length;

            if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                assert _inPayloadOff < _inPayload.length;
                throw new ExJingle("read payload returns " + res + " error "
                        + error[0]);
            } else if (res == StreamResult.SR_BLOCK) {
                assert _inPayloadOff < _inPayload.length;
                break;
            }

            assert res == StreamResult.SR_SUCCESS;
            assert _inPayloadOff == _inPayload.length;

            ////////
            // deliver the data to the upper layer

            ByteArrayInputStream is = new ByteArrayInputStream(_inPayload);
            PBTPHeader transhdr = TPUtil.processUnicastHeader(is);
            if (TPUtil.isPayload(transhdr)) {
                ij.processUnicastPayload(did, transhdr, is, msgHdrLen + msgPldLen);
            } else {
                ij.processUnicastControl(did, transhdr);
            }

            // reset input buffers
            _inHeaderOff = 0;
            _inPayload = null;
            _inPayloadOff = 0;
        }
    }

    /**
     * Log a network I/O operation. This provides a consistent way to log
     * sending/receiving bytes from a peer. Log messages are of the form:
     * <br/>
     * <br/>
     * <strong>send:</strong>
     * <code>jch: send: b:{$inthiscall} [{$sofar}/{$expected}] -> $did ret: ${ioretval}</code>
     * <strong>recv:</strong>
     * <code>jch: recv: b:{$inthiscall} [{$sofar}/{$expected}] <- $did ret: ${ioretval}</code>
     *
     * @param inthiscall bytes transferred in this I/O call
     * @param sofar bytes transferred so far
     * @param expected bytes expected to be transferred (for this payload, header, etc.)
     * @param ioretval {@link StreamResult} return value from this I/O operation
     * @param iswrite <code>true</code> if this is a <code>write_()</code>,
     * <code>false</code> if not
     */
    private void logio(long inthiscall, long sofar, long expected, StreamResult ioretval, boolean iswrite)
    {
        String optyp = (iswrite ? "send" : "recv");
        String opdir = (iswrite ? " -> " : " <- ");

        if (l.isDebugEnabled()) {
            l.debug("jds: " + optyp + " b:" + inthiscall + " [" + sofar + "/" + expected + "]" + opdir + did + " ret:" + ioretval);
        }
    }

    @Override
    public void finalize()
    {
        // delete_() must have been called
        assert StreamInterface.getCPtr(_streamInterface) == 0;
    }

    @Override
    public String toString()
    {
        return (_incoming ? "I" : "O") + did;
    }

    @Override
    public void delete_()
    {
        _streamInterface.delete();
        _slotEvent.delete();
    }

    long getBytesIn_()
    {
        return _bytesIn;
    }

    String diagnose_()
    {
        //return _s.diagnose(); // FIXME (AG): need to add a diagnose method!
        return "";
    }
}
