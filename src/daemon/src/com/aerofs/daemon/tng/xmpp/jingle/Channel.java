/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.j.StreamEvent;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.StreamInterface_EventSlot;
import com.aerofs.j.StreamResult;
import com.aerofs.j.StreamState;
import com.aerofs.j.j;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.base.id.DID;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// A Channel object represents a bidirectional jingle stream
//
// N.B. all the methods of this class must be called within the signal thread.
//
final class Channel implements IProxyObjectContainer
{
    private static final Logger l = Util.l(Channel.class);

    static interface IClosureListener
    {
        void closed_(Channel self);
    }

    static interface IConnectionListener
    {
        void connected_(Channel self);
    }

    private final IJingle ij;
    private final DID did;
    private final StreamInterface _s;
    private boolean _writable;
    private boolean _firstwrite = false;
    private long _bytesIn;

    private final PrioQueue<SendData> _q = new PrioQueue<SendData>(DaemonParam.Jingle.QUEUE_LENGTH);

    // each SendData is an atomic chunk of data and can't be preempted by other
    // data even if they are at higher priorities. therefore we have to remember
    // the current data entry in a place other than the queue head.
    private SendData _outCur;
    private int _outOff;    // the offset of the current byte array of _outCur

    private final byte[] _inHeader = new byte[getHeaderLen()];
    private int _inHeaderOff;

    private byte[] _inPayload;
    private int _inPayloadOff;

    private final IClosureListener _closl;
    private final IConnectionListener _connl;
    private final boolean _incoming;

    // keep a reference to prevent the slot being GC'ed (as there's no Java
    // reference to this object otherwise)
    //
    private final StreamInterface_EventSlot _slotEvent = new StreamInterface_EventSlot()
    {
        @Override
        public void onEvent(StreamInterface s, int event, int error)
        {
            try {
                onStreamEvent_(s, event, error);
            } catch (Exception e) {
                close_(e);
                _closl.closed_(Channel.this);
            }
        }
    };

    Channel(IJingle ij, StreamInterface s, DID did, boolean incoming, IClosureListener closl,
            IConnectionListener connl)
    {
        this.ij = ij;
        this.did = did;
        _s = s;
        _closl = closl;
        _connl = connl;
        _incoming = incoming;

        _slotEvent.connect(_s);
        _writable = _s.GetState() == StreamState.SS_OPEN;
    }

    boolean isIncoming()
    {
        return _incoming;
    }

    DID did()
    {
        return did;
    }

    private void onStreamEvent_(StreamInterface s, int event, int error)
            throws Exception
    {
        if ((event & StreamEvent.SE_WRITE.swigValue()) != 0) {
            if (!_firstwrite) {
                _firstwrite = true;
                _connl.connected_(Channel.this);
            }

            _writable = true;

            write_();
        }

        if ((event & StreamEvent.SE_READ.swigValue()) != 0) {
            read_();
        }

        if ((event & StreamEvent.SE_CLOSE.swigValue()) != 0) {
            throw new ExJingle("recv SE_CLOSE error " + error);
        }
    }

    // calling this method will not trigger IClosureListener
    void close_(Exception e)
    {
        l.info("close channel " + this + ": " + Util.e(e, ExJingle.class));

        _s.Close();

        if (_outCur != null && _outCur.getCompletionFuture() != null) {
            _outCur.getCompletionFuture().setException(e);
        }

        while (!_q.isEmpty_()) {
            SendData sd = _q.dequeue_();
            if (sd.getCompletionFuture() != null) {
                sd.getCompletionFuture().setException(e);
            }
        }
    }

    void send_(byte[][] bss, Prio prio, UncancellableFuture<Void> future)
    {
        SendData sd = new SendData(bss, future);

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
            if (future != null) {
                future.setException(e);
            } else {
                l.info("silently drop packet");
            }
        }
    }

    private void write_()
            throws ExJingle
    {
        assert _writable;

        int[] written = {0};
        int[] error = {0};

        // iterate through data entries
        while (_outCur != null) {

            // itereate through byte arrays in each data entry
            while (true) {
                byte[] cur = _outCur.current();
                if (cur == null) {
                    if (_outCur.getCompletionFuture() != null)
                        _outCur.getCompletionFuture().set(null);
                    break;
                }

                StreamResult res;

                res = j.WriteAll(_s, cur, _outOff, cur.length - _outOff, written, error);

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

    private void read_()
            throws Exception
    {
        int[] read = {0};
        int[] error = {0};
        int msgHdrLen = 0;
        int msgPldLen;
        StreamResult res;
        while (true) {

            ////////
            // read header

            if (_inHeaderOff != _inHeader.length) {
                assert _inHeaderOff < _inHeader.length;
                // read the header
                res = j.ReadAll(_s, _inHeader, _inHeaderOff, _inHeader.length - _inHeaderOff, read,
                        error);
                _inHeaderOff += read[0];
                logio(read[0], _inHeaderOff, _inHeader.length, res, false);
                msgHdrLen = read[0];
                ij.addBytesRx(read[0]);

                if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                    assert _inHeaderOff < _inHeader.length;
                    throw new ExJingle("read header returns " + res + " error " + error[0]);
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
                int len = readHeader(_inHeader);
                _inPayload = new byte[len];
            }

            assert _inPayloadOff < _inPayload.length;

            res = j.ReadAll(_s, _inPayload, _inPayloadOff, _inPayload.length - _inPayloadOff, read,
                    error);
            _inPayloadOff += read[0];
            logio(read[0], _inPayloadOff, _inPayload.length, res, false);
            msgPldLen = read[0];
            ij.addBytesRx(read[0]);
            _bytesIn += read[0];

            assert _inPayloadOff <= _inPayload.length;

            if (res == StreamResult.SR_ERROR || res == StreamResult.SR_EOS) {
                assert _inPayloadOff < _inPayload.length;
                throw new ExJingle("read payload returns " + res + " error " + error[0]);
            } else if (res == StreamResult.SR_BLOCK) {
                assert _inPayloadOff < _inPayload.length;
                break;
            }

            assert res == StreamResult.SR_SUCCESS;
            assert _inPayloadOff == _inPayload.length;

            ////////
            // deliver the data to the upper layer

            ij.processData(did, _inPayload, msgHdrLen + msgPldLen);

            // reset input buffers
            _inHeaderOff = 0;
            _inPayload = null;
            _inPayloadOff = 0;
        }
    }

    /**
     * Log a network I/O operation. This provides a consistent way to log sending/receiving bytes
     * from a peer. Log messages are of the form: <br/> <br/> <strong>send:</strong> <code>jch:
     * send: b:{$inthiscall} [{$sofar}/{$expected}] -> $did ret: ${ioretval}</code>
     * <strong>recv:</strong> <code>jch: recv: b:{$inthiscall} [{$sofar}/{$expected}] <- $did ret:
     * ${ioretval}</code>
     *
     * @param inthiscall bytes transferred in this I/O call
     * @param sofar bytes transferred so far
     * @param expected bytes expected to be transferred (for this payload, header, etc.)
     * @param ioretval {@link StreamResult} return value from this I/O operation
     * @param iswrite <code>true</code> if this is a <code>write_()</code>, <code>false</code> if
     * not
     */
    private void logio(long inthiscall, long sofar, long expected, StreamResult ioretval,
            boolean iswrite)
    {
        String optyp = (iswrite ? "send" : "recv");
        String opdir = (iswrite ? " -> " : " <- ");

        if (l.isInfoEnabled()) {
            l.info("jch: " + optyp + " b:" + inthiscall + " [" + sofar + "/" + expected + "]" +
                    opdir + did + " ret:" + ioretval);
        }
    }

    @Override
    public void finalize()
    {
        // delete_() must have been called
        assert StreamInterface.getCPtr(_s) == 0;
    }

    @Override
    public String toString()
    {
        return (_incoming ? "I" : "O") + did;
    }

    @Override
    public void delete_()
    {
        _s.delete();
        _slotEvent.delete();
    }

    long getBytesIn_()
    {
        return _bytesIn;
    }

    String diagnose_()
    {
        return _s.diagnose();
    }

    /**
     * @return body length
     */
    public static int readHeader(byte[] header)
            throws ExFormatError
    {
        try {
            assert header.length == getHeaderLen();
            DataInputStream is = new DataInputStream(new ByteArrayInputStream(header));
            int magic = is.readInt();
            if (magic != C.CORE_MAGIC) {
                throw new ExFormatError("magic doesn't match. expect " +
                        C.CORE_MAGIC + " received " + magic);
            }

            int len = is.readInt();
            if (len <= 0 || len > DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE) {
                throw new ExFormatError("insane msg len " + len);
            }

            return len;

        } catch (IOException e) {
            assert false;
            return -1;
        }
    }

    /**
     *
     * @param bodylen
     * @return
     */
    public static byte[] writeHeader(int bodylen)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(getHeaderLen());
        DataOutputStream os = new DataOutputStream(bos);
        try {
            os.writeInt(C.CORE_MAGIC);
            os.writeInt(bodylen);
            os.close();
        } catch (Exception e) {
            assert false;
        }

        return bos.toByteArray();
    }

    /**
     *
     * @return
     */
    public static int getHeaderLen()
    {
        return Integer.SIZE * 2 / Byte.SIZE;
    }
}
