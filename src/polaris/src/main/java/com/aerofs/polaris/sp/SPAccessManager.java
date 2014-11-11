package com.aerofs.polaris.sp;

import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;

final class SPAccessManager implements AccessManager {

    @Override
    public void checkAccess(String identity, String oid, Access... accesses) throws AccessException {
        // noop
        // FIXME (AG): how am I going to deal with this potentially blocking and taking out the request thread?
    }
}
