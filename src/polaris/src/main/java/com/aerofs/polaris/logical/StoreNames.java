package com.aerofs.polaris.logical;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.ids.UniqueID;

import java.io.IOException;

public interface StoreNames {
    boolean renameStore(AeroUserDevicePrincipal principal, UniqueID oid, String name);

    String getStoreDefaultName(AeroUserDevicePrincipal principal, UniqueID store) throws IOException;

    void setPersonalStoreName(AeroUserDevicePrincipal principal, UniqueID store, String name) throws IOException;
}
