package com.aerofs.daemon.core.net.dtls;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.dtls.DTLSLayer.Footer;
import com.aerofs.daemon.lib.DaemonParam.DTLS;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDTLS;
import com.aerofs.swig.dtls.DTLSEngine;
import com.aerofs.swig.dtls.DTLSEngine.DTLS_RETCODE;

// TODO use separate object for sender and receiver. the receiver should not
// have the queue
//
class DTLSEntry
{
    private static final Logger l = Util.l(DTLSEntry.class);

    private final DTLSEngine _engine;
    private final DTLSLayer _layer;
    final PrioQueue<DTLSMessage<byte[]>> _sendQ;
    long _lastHshakeMsgTime;
    String _user;
    private boolean _hshakeDone;

    DTLSEntry(DTLSLayer layer, DTLSEngine engine)
    {
        _layer = layer;
        _lastHshakeMsgTime = System.currentTimeMillis();
        _engine = engine;
        _sendQ = new PrioQueue<DTLSMessage<byte[]>>(DTLS.HS_QUEUE_SIZE);
    }

    boolean isHshakeDone()
    {
        // because calling C is expensive, so we cache the result.
        if (_hshakeDone) {
            return true;
        } else if (_engine.isHshakeDone()) {
            _hshakeDone = true;
            return true;
        } else {
            return false;
        }
    }

    String getPeerCName()
    {
        byte[] buf = new byte[128];
        _engine.getPeerCName(buf, buf.length);
        return Util.cstring2string(buf, false);
    }

    /**
     * @param hsSent returns whether a handshake message was sent
     * @return the data that's to be pushed to the lower layer
     */
    byte[] encrypt(byte[] bs, PeerContext pc, Footer footer,
            OutArg<Boolean> hsSent) throws Exception
    {
        l.info("enc msg " + bs.length);
        DTLS_RETCODE rc = DTLS_RETCODE.DTLS_OK;

        if (null == _engine) {
            l.warn("can't get/create eng. eng is null");
            throw new ExDTLS("can't get/create eng. eng is null");
        }

        byte[] bsToSend = new byte[DTLS.BUF_SIZE]; // hardcoded number for now
        int[] outputLen = { bsToSend.length - Footer.SIZE };

        rc = cryptImpl_(true, bs, bsToSend, outputLen);

        if (DTLS_RETCODE.DTLS_NEEDREAD == rc
                || DTLS_RETCODE.DTLS_NEEDWRITE == rc) {
            l.info("eng ret need r/w, with msg " + outputLen[0]);

            if (outputLen[0] > 0) {
                bsToSend[outputLen[0]] = footer.toByte();
                byte[] temp =
                        Arrays.copyOf(bsToSend, outputLen[0] + Footer.SIZE);
                l.info("hs: send msg down " + outputLen[0]);
                _layer.lower().sendUnicastDatagram_(temp, pc);
                if (hsSent != null) hsSent.set(true);
            } else {
                l.warn("enc ret size 0");
            }
            return null;

        } else if (DTLS_RETCODE.DTLS_OK == rc) {
            l.info("eng enc'ed msg " + outputLen[0]);
            bsToSend[outputLen[0]] = (byte) footer.ordinal();
            return Arrays.copyOf(bsToSend, outputLen[0] + Footer.SIZE);

        } else {
            // the DTLSCtx should not be removed from cache here
            // instead it should be removed at the upper layer where the exception is caught
            //  _dtlsSendCache.removeDTLSCtxFromCache_(pc);
            throw new ExDTLS("eng ret gen err " + rc + " : " + pc);
        }
    }

    /**
     * @return the data that's to be delivered to the upper layer
     */
    ByteArrayInputStream decrypt(byte[] input, PeerContext pc, Footer footer,
            OutArg<Boolean> hsSent)
        throws Exception
    {
        l.info("dec msg " + input.length);
        DTLS_RETCODE rc = DTLS_RETCODE.DTLS_OK;

        byte[] output = new byte[DTLS.BUF_SIZE]; // hardcoded number for now
        int[] outputLen = { output.length - Footer.SIZE };

        rc = cryptImpl_(false, input, output, outputLen);

        if (DTLS_RETCODE.DTLS_NEEDREAD == rc
                || DTLS_RETCODE.DTLS_NEEDWRITE == rc) {
            l.info("eng ret need r/w, drop packet & send msg " +
                    outputLen[0]);

            if (outputLen[0] > 0) {
                output[outputLen[0]] = (byte) footer.ordinal();
                byte[] temp = Arrays.copyOf(output, outputLen[0] + Footer.SIZE);
                _layer.lower().sendUnicastDatagram_(temp, pc);
                hsSent.set(true);
            }

            return null;

        } else if (DTLS_RETCODE.DTLS_OK == rc) {

            return new ByteArrayInputStream(output, 0, outputLen[0]);

        } else {
            throw new ExDTLS("eng ret gen err " + rc + " : " + pc);
        }
    }

    private DTLS_RETCODE cryptImpl_(boolean encrypt, byte[] input, byte[] output,
          int[] outputLen)
    {
        l.debug("cryptImpl_: input length: " + input.length);
        return encrypt ?
              _engine.encrypt(input, output, input.length, outputLen) :
              _engine.decrypt(input, output, input.length, outputLen);
    }

// parallel cryption disabled as it causes some unknown concurrency issues
// in the OpenSSL library when the server load is high
//
//    private int _cryptingThreads;
//    private int _seq;
//    private final TreeMap<Integer, TCB> _waiters = new TreeMap<Integer, TCB>();
//
//    /**
//     * during handshake decryption may take a fairly long time (>300 ms), so we
//     * parallelize handshake decryption. because encryption shares the same
//     * engine, it needs to be serialized, too. packats must be {en,de}crypted
//     * in order, and therefore we use the waiter queue instead of a simple
//     * synchronized block
//     */
//    private DTLS_RETCODE cryptImpl_(boolean encrypt, byte[] input, byte[] output,
//            int[] outputLen) throws ExAborted
//    {
//        // calling isHshakeDone() is not strictly thread-safe here, considering
//        // hs decryption may be called in another, concurrent thread. however,
//        // because 1) the implementation of _engine.isHshakeDone allows
//        // concurrent access without side-effects, and 2) _cryptingThreads is
//        // greater than 0, which shortcuts the !hsDecrypt test below, if there
//        // is any hs decryption thread.
//        //
//        boolean hsDecrypt = !encrypt && !isHshakeDone();
//        if (_cryptingThreads == 0 && !hsDecrypt) {
//            return encrypt ?
//                    _engine.encrypt(input, output, input.length, outputLen) :
//                    _engine.decrypt(input, output, input.length, outputLen);
//        }
//
//        _cryptingThreads++;
//        try {
//            if (_cryptingThreads != 1) {
//                Token tk = Cat.UNLIMITED.acquire_("wait for cryption");
//                try {
//                    int seq = _seq++;
//                    TCB tcb = TC.tcb();
//                    _waiters.put(seq, tcb);
//                    try {
//                        _layer.c().tc().pause_(tk, "wait for cryption");
//                    } finally {
//                        Util.verify(_waiters.remove(seq) == tcb);
//                    }
//                } finally {
//                    tk.reclaim_();
//                }
//            }
//
//            if (hsDecrypt) {
//                // run hand-shake decryption out of the core context
//                Token tk = Cat.UNLIMITED.acquire_("hsDecrypt");
//                try {
//                    TCB tcb = _layer.c().tc().pseudoPause_(tk, "hsDecrypt");
//                    try {
//                        assert !encrypt;
//                        return _engine.decrypt(input, output, input.length,
//                                outputLen);
//                    } finally {
//                        tcb.pseudoResumed_();
//                    }
//                } finally {
//                    tk.reclaim_();
//                }
//
//            } else {
//                    return encrypt ?
//                        _engine.encrypt(input, output, input.length, outputLen) :
//                        _engine.decrypt(input, output, input.length, outputLen);
//            }
//
//        } finally {
//            assert _cryptingThreads > 0;
//            _cryptingThreads--;
//
//            Entry<Integer, TCB> en = _waiters.firstEntry();
//            if (en != null) en.getValue().resume_();
//        }
//    }

    private boolean _draining;

    boolean isDraining_()
    {
        return _draining;
    }

    public void setDraining_(boolean b)
    {
        _draining = b;
    }
}
