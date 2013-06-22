package com.aerofs.daemon.event.net.rx;

import java.io.InputStream;

/**
 * An abstraction of an input buffer
 *
 * <b>IMPORTANT:</b> Implementers must guarantee that implementations of methods
 * in this interface are non-blocking. Implementers must further guarantee that
 * any classes returned via these methods only provide non-blocking methods.
 */
public interface IInputBuffer
{
    /**
     * Getter
     *
     * @return {@link java.io.ByteArrayInputStream} from which the packet's payload can
     * be read
     */
    InputStream is();

    /**
     * Getter
     *
     * @return original length of the packet on the wire. This includes both
     * the transport and payload length
     */
    int wireLength();
}
