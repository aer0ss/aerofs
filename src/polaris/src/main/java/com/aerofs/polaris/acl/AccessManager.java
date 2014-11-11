package com.aerofs.polaris.acl;

public interface AccessManager {

    void checkAccess(String identity, String oid, Access... accesses) throws AccessException;
}
