package com.aerofs.polaris;

import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.acl.AccessType;

final class SPAccessManager implements AccessManager {

    SPAccessManager() {
        // noop
    }

    @Override
    public boolean canAccess(String identity, String oid, AccessType... accessTypes) {
        return true;
    }
}
