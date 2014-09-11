package com.aerofs.polaris.acl;

public interface AccessManager {

    boolean canAccess(String identity, String oid, AccessType... accessTypes);
}
