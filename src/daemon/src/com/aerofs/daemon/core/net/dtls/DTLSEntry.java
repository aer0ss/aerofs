package com.aerofs.daemon.core.net.dtls;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import org.slf4j.Logger;

import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.dtls.DTLSLayer.Footer;
import com.aerofs.daemon.lib.DaemonParam.DTLS;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDTLS;
import com.aerofs.swig.dtls.DTLSEngine;
import com.aerofs.swig.dtls.DTLSEngine.DTLS_RETCODE;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO use separate object for sender and receiver. the receiver should not
// have the queue
//
class DTLSEntry
{
    private static final Logger l = Loggers.getLogger(DTLSEntry.class);

    /**
     * When delaying a message, we must keep track of the PeerContext to which is was supposed to be
     * sent. This is important because the DTLSEntry can be used with PeerContexts concerning
     * different stores and the SID is part of the transport header (NB: this really shouldn't be
     * the case for unicast messages btw...) and derived form the PeerContext which can lead to
     * "corruption" of the header of delayed messages (with symptoms including but not limited to
     * spontaneous appearance of ghost KMLs and heisenbugs in the sync algorithm)
     */
    static class DelayedDTLSMessage
    {
        public final PeerContext _pc;
        public final DTLSMessage<byte[]> _msg;

        DelayedDTLSMessage(PeerContext pc, DTLSMessage<byte[]> msg)
        {
            _pc = pc;
            _msg = msg;
        }
    }

    private final DTLSEngine _engine;
    private final DTLSLayer _layer;
    final PrioQueue<DelayedDTLSMessage> _sendQ;
    final ElapsedTimer _handshakeMessageTimer;
    UserID _user;
    private boolean _hshakeDone;

    DTLSEntry(DTLSLayer layer, DTLSEngine engine)
    {
        _layer = layer;
        _handshakeMessageTimer = new ElapsedTimer();
        _engine = engine;
        _sendQ = new PrioQueue<DelayedDTLSMessage>(DTLS.HS_QUEUE_SIZE);
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
    byte[] encrypt(byte[] bs, PeerContext pc, Footer footer, @Nullable OutArg<Boolean> hsSent)
            throws Exception
    {
        l.trace("enc msg {}", bs.length);

        if (null == _engine) {
            l.warn("can't get/create eng. eng is null");
            throw new ExDTLS("can't get/create eng. eng is null");
        }

        byte[] bsToSend = new byte[DTLS.BUF_SIZE]; // hardcoded number for now
        int[] outputLen = { bsToSend.length - Footer.SIZE };

        DTLS_RETCODE rc = cryptImpl_(true, bs, bsToSend, outputLen);

        if (DTLS_RETCODE.DTLS_NEEDREAD == rc
                || DTLS_RETCODE.DTLS_NEEDWRITE == rc) {
            l.trace("eng ret need r/w, with msg {}", outputLen[0]);

            if (outputLen[0] > 0) {
                bsToSend[outputLen[0]] = footer.toByte();
                byte[] temp =
                        Arrays.copyOf(bsToSend, outputLen[0] + Footer.SIZE);
                l.trace("hs: send msg down {}", outputLen[0]);
                _layer.lower().sendUnicastDatagram_(temp, pc);
                if (hsSent != null) hsSent.set(true);
            } else {
                l.warn("enc ret size 0");
            }
            return null;

        } else if (DTLS_RETCODE.DTLS_OK == rc) {
            l.trace("eng enc'ed msg {}", outputLen[0]);
            bsToSend[outputLen[0]] = footer.toByte();
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
    ByteArrayInputStream decrypt_(byte[] input, PeerContext pc, Footer footer,
            @Nonnull OutArg<Boolean> hsSent)
        throws Exception
    {
        l.trace("dec msg {}", input.length);

        byte[] output = new byte[DTLS.BUF_SIZE]; // hardcoded number for now
        int[] outputLen = { output.length - Footer.SIZE };

        DTLS_RETCODE rc = cryptImpl_(false, input, output, outputLen);

        if (DTLS_RETCODE.DTLS_NEEDREAD == rc || DTLS_RETCODE.DTLS_NEEDWRITE == rc) {
            l.trace("eng ret need r/w, drop packet & send msg {}", outputLen[0]);

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
        l.trace("cryptImpl_: enc? {} input length: {}", encrypt, input.length);
        return encrypt ?
              _engine.encrypt(input, output, input.length, outputLen) :
              _engine.decrypt(input, output, input.length, outputLen);
    }

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
