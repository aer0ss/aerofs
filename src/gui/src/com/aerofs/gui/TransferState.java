package com.aerofs.gui;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.aerofs.ui.UI;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

/**
 * This class represents the transfer states as far as the GUI is aware of.
 *
 * It listens to ritual notifications, maintains the latest states, and
 *   notifies its listeners whenever its states changed.
 *
 * It process ritual notifications on the GUI thread, and when it notifies
 *   its listeners, it does so on the GUI thread.
 */
public class TransferState
{
    private final Logger l = Loggers.getLogger(TransferState.class);

    private final Table<SOCID, DID, PBTransferEvent> _states;
    private final List<ITransferStateChangedListener> _listeners;

    public TransferState(RitualNotificationClient rnc)
    {
        _states = HashBasedTable.create();
        _listeners = Lists.newArrayList();

        rnc.addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(final PBNotification pb)
            {
                if (pb.getType() == Type.TRANSFER) {
                    PBTransferEvent t = pb.getTransfer();
                    SOCID socid = new SOCID(t.getSocid());
                    DID did = new DID(BaseUtil.fromPB(t.getDeviceId()));

                    l.debug("update transfer: {} {} {} {}/{}",
                            socid, t.getUpload() ? "to" : "from", did, t.getDone(), t.getTotal());
                }
                UI.get().asyncExec(() -> updateTransfers(pb));
            }

            @Override
            public void onNotificationChannelBroken()
            {
                UI.get().asyncExec(() -> clearTransfers());
            }
        });
    }

    /**
     * Nop if the notification type is not download or upload
     * Precondition: we must be on the GUI thread
     */
    private synchronized void updateTransfers(PBNotification pb)
    {
        if (pb.getType() == Type.TRANSFER) {
            updateTransferState_(pb.getTransfer());
            notifyListeners();
        }
    }

    private synchronized void clearTransfers()
    {
        l.debug("clear transfers");
        _states.clear();
        notifyListeners();
    }

    private void updateTransferState_(PBTransferEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());
        DID did = new DID(BaseUtil.fromPB(pb.getDeviceId()));

        l.debug("update transfer: {} {} {} {}/{}",
                socid, pb.getUpload() ? "to" : "from", did, pb.getDone(), pb.getTotal());

        if (pb.getDone() == pb.getTotal()) {
            _states.remove(socid, did);
        } else {
            _states.put(socid, did, pb);
        }
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Collection<PBTransferEvent> transfers_()
    {
        return _states.values();
    }

    public interface ITransferStateChangedListener
    {
        void onTransferStateChanged(TransferState state);
    }

    public void addListener(ITransferStateChangedListener listener)
    {
        synchronized (_listeners) {
            _listeners.add(listener);
        }
    }

    public void removeListener(ITransferStateChangedListener listener)
    {
        synchronized (_listeners) {
            _listeners.remove(listener);
        }
    }

    private void notifyListeners()
    {
        synchronized (_listeners) {
            for (ITransferStateChangedListener listener : _listeners) {
                listener.onTransferStateChanged(TransferState.this);
            }
        }
    }
}
