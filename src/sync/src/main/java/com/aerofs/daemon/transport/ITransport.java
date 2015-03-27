package com.aerofs.daemon.transport;

import com.aerofs.daemon.lib.ITransferStat;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.ids.DID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;

// FIXME (AG): this API is deprecated and will broken up.
/**
 * Implemented by classes that provide a message transport mechanism.
 */
public interface ITransport extends ITransferStat
{
    /**
     * Initialize internal state for the transport.
     *
     * @throws Exception if there is any setup error; if an exception is thrown this
     * module cannot be started and cannot be used
     */
    void init() throws Exception;

    /**
     * Start the transport.
     * <br/>
     * Expects that {@link ITransport#init()} has been called first.
     * This method should <strong>not</strong> throw. Throwable tasks should be performed
     * during initialization.
     * <br/>
     * If {@code start()} was previously called, subsequent calls are noops.
     */
    void start();

    /**
     * Stop the transport.
     * <br/>
     * If {@code stop()} was previously called, subsequent calls are noops.
     */
    void stop();

    /**
     * Return a human-readable transport identifier.
     *
     * @return <em>constant</em> identifier for the transport.
     */
    String id();

    // TODO (AG): Remove this
    /**
     * Returna a ranking (relative to other transport implementations)
     * of how preferred this transport implementation is.
     *
     * @return <em>constant</em> ranking relative to siblings for the transport.
     */
    int rank();

    /**
     * Indicate whether multicast is supported by the transport implementation.
     *
     * @return true if multicast is supported, false otherwise
     */
    boolean supportsMulticast();

    /**
     * Return the transport event queue into which incoming transport
     * calls can be made.
     *
     * @return queue by which you can deliver events to this module.
     */
    IBlockingPrioritizedEventSink<IEvent> q();

    OutgoingStream newOutgoingStream(DID did) throws ExDeviceUnavailable, ExTransportUnavailable;

    /**
     * Return transport diagnostics.
     *
     * @param transportDiagnostics message builder that should be populated with the diagnostics
     */
    void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics);
}
