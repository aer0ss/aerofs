package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.ex.ExWrapped;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.ExSenderHasNoPerm;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.ids.DID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IAsyncDownload
{
    static final Logger logger = Loggers.getLogger(IAsyncDownload.class);

    static final int MAX_UPDATE_RETRY = 2;
    static final long UPDATE_RETRY_DELAY = 3 * C.SEC;

    /**
     * Exception thrown when processing a reply to a GetComponentCall
     * TODO: further distinguish between remote (specified in reply) and local exceptions
     */
    class ExProcessReplyFailed extends ExWrapped
    {
        private static final long serialVersionUID = 0L;
        public final DID _did;
        ExProcessReplyFailed(DID did, Exception e) { super(e); _did = did; }
    }

    /**
     * Exception thrown before a remote GetComponentCall completed
     */
    class ExRemoteCallFailed extends ExWrapped
    {
        private static final long serialVersionUID = 0L;
        ExRemoteCallFailed(Exception e) { super(e); }
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     * and inform listeners of success/failure appropriately
     */
    default void do_(SOID soid, Token tk, Map<DID, Exception> did2e,
                     List<IDownloadCompletionListener> listeners)
    {
        try {
            final DID replier = doImpl_();
            notifyListeners_(listener -> listener.onDownloadSuccess_(soid, replier), listeners);
        } catch (ExNoAvailDevice e) {
            logger.warn("{}: ", soid, BaseLogUtil.suppress(e));
            // This download object tracked all reasons (Exceptions) for why each device was
            // avoided. Thus if the To object indicated no devices were available, then inform
            // the listener about all attempted devices, and why they failed to deliver the socid.
            notifyListeners_(listener -> listener.onPerDeviceErrors_(soid, did2e), listeners);
        } catch (RuntimeException e) {
            // we don't want the catch-all block to swallow runtime exceptions
            SystemUtil.fatal(e);
        } catch (final Exception e) {
            logger.warn("{} :", soid, BaseLogUtil.suppress(e, ExNoPerm.class));
            notifyListeners_(listener -> listener.onGeneralError_(soid, e), listeners);
        } finally {
            tk.reclaim_();
        }
    }

    @Nullable DID doImpl_() throws IOException, SQLException, ExNoAvailDevice,
            ExAborted, ExWrapped, ExNoPerm, ExSenderHasNoPerm, ExOutOfSpace;

    default void handleProcessReplyFailed(ExProcessReplyFailed e, SOID soid, To from,
            Map<DID, Exception> did2e) throws ExNoPerm, ExOutOfSpace, IOException
    {
        if (e._e instanceof ExNoPerm) {
            // collector should only collect permitted components. no_perm may happen when
            // other user just changed the permission before this call.
            logger.error("{}: we have no perm", soid);
            throw (ExNoPerm)e._e;
        } else if (e._e instanceof ExSenderHasNoPerm) {
            logger.error("{}: sender has no perm", soid);
            avoidDevice_(e._did, e, from, did2e);
        } else if (e._e instanceof ExNoComponentWithSpecifiedVersion) {
            logger.info("{} from {}:", soid, e._did, BaseLogUtil.suppress(e._e));
            avoidDevice_(e._did, e._e, from, did2e);
        } else if (e._e instanceof ExOutOfSpace) {
            throw (ExOutOfSpace)e._e;
        } else if (e._e instanceof IOException) {
            // TODO: make sure we only abort in case of local I/O error
            throw (IOException)e._e;
        } else {
            logger.info("gcr fail {} from {}: ", soid, e._did, e._e);

            onGeneralException(soid, e._e, e._did, from, did2e);
        }
    }

    default void handleRemoteCallFailed(ExRemoteCallFailed e, SOID soid, To from,
            Map<DID, Exception> did2e)
    {
        // NB: remote errors in the GCR are wrapped in ExProcessReplyFailed...
        logger.info("gcc fail {}: ", soid, e._e);
        onGeneralException(soid, e._e, null, from, did2e);
    }

    static interface IDownloadCompletionListenerVisitor
    {
        void notify_(IDownloadCompletionListener l);
    }

    default void notifyListeners_(IDownloadCompletionListenerVisitor visitor,
                                  List<IDownloadCompletionListener> listeners)
    {
        logger.debug("notify {} listeners", listeners.size());
        for (IDownloadCompletionListener lst : listeners) {
            logger.debug("  notify {}", lst);
            visitor.notify_(lst);
        }
    }

    default void onGeneralException(SOID soid, Exception e, DID replier, To from, Map<DID,
            Exception> did2e)
    {
        if (e instanceof RuntimeException) SystemUtil.fatal(e);

        // RTN: retry now
        logger.warn("{} : {} RTN ",soid, replier, BaseLogUtil.suppress(e,
                ExAborted.class, ExNoAvailDevice.class, ExTimeout.class, ExStreamInvalid.class));
        if (replier != null) avoidDevice_(replier, e, from, did2e);
    }

    default void avoidDevice_(DID replier, Exception e, To from, Map<DID, Exception> did2e)
    {
        // NB: is this really necessary?
        // To.pick_() already calls avoid_() so the only case where a second call makes a difference
        // is when a new download request was made for the same object while the core lock was
        // released around a remote call. I can't help but wonder if calling avoid_() in such a case
        // is actually a bug...
        from.avoid_(replier);
        did2e.put(replier, e);
    }

    default void include_(Set<DID> dids, IDownloadCompletionListener completionListener, To from,
            List<IDownloadCompletionListener> listeners)
    {
        dids.forEach(from::add_);
        listeners.add(completionListener);
    }

    default void onUpdateInProgress(DID did, int updateRetry, SOID soid, Token tk) throws ExAborted
    {
        // too many retries: abort dl to free token
        // the collector will retry at a later time (i.e. on next iteration)
        if (updateRetry > MAX_UPDATE_RETRY) {
            logger.warn("{} {}: update in prog for too long. abort", did, soid);
            throw new ExAborted("update in progress");
        }

        logger.info("{} {}: update in prog. retry later", did, soid);
        tk.sleep_(UPDATE_RETRY_DELAY, "retry dl (update in prog)");
    }
}
