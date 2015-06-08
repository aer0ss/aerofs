package com.aerofs.daemon.transport.presence;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;

public interface IStoreInterestListener {
    void onDeviceJoin(DID did, SID sid);
    void onDeviceLeave(DID did, SID sid);
}
