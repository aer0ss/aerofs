package com.aerofs.polaris.logical;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.ids.UniqueID;

public interface StoreRenamer {
    boolean renameStore(AeroUserDevicePrincipal principal, UniqueID oid, String name);
}
