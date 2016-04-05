package com.aerofs.polaris.logical;

import com.aerofs.auth.server.delegated.AeroDelegatedUserDevicePrincipal;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.OperationResult;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.MountPoints;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class SFAutoJoinAndLeave implements SFMemberChangeListener
{
    private static final Logger l = LoggerFactory.getLogger(SFAutoJoinAndLeave.class);

    private final ObjectStore objectStore;
    private final DBI dbi;
    private final Notifier notifier;
    private final StoreNames storeNames;

    @Inject
    public SFAutoJoinAndLeave(ObjectStore objectStore, DBI dbi, Notifier notifier, StoreNames storeNames) {
        this.objectStore = objectStore;
        this.dbi = dbi;
        this.notifier = notifier;
        this.storeNames = storeNames;
    }


    @Override
    public void userLeftStore(UserID user, SID store) {
        Preconditions.checkArgument(!store.isUserRoot(), "user cannot leave root store");
        l.info("removing user {} from store {}", user, store);
        SID userRoot = SID.rootSID(user);
        OperationResult result = null;

        // messy while loop because there's a race condition of an interleaved transaction
        // but we don't want to acquire a lock within a transaction
        while (result == null) {
            UniqueID parent = dbi.inTransaction((conn, status) -> conn.attach(MountPoints.class).getMountPointParent(userRoot, store));

            if (parent != null) {
                Lock l = objectStore.lockObject(parent);
                try {
                    result = dbi.inTransaction((conn, status) -> {
                        DAO dao = new DAO(conn);
                        if (parent.equals(dao.mountPoints.getMountPointParent(userRoot, store))) {
                            return objectStore.performTransform(dao,
                                    objectStore.checkAccessForStores(user, Sets.newHashSet(userRoot), Access.READ, Access.WRITE),
                                    DID.DUMMY, parent, new RemoveChild(SID.storeSID2anchorOID(store)));
                        } else {
                            return null;
                        }
                    });
                } finally {
                    l.unlock();
                }
            } else {
                // something else has deleted the shared folder
                break;
            }
        }

        if (result != null) {
            result.updated.forEach(x -> notifier.notifyStoreUpdated(x.object.store, x.transformTimestamp));
        }
    }

    @Override
    public void userJoinedStore(UserID user, SID store) {
        Preconditions.checkArgument(!store.isUserRoot(), "user cannot join root store");
        l.info("adding user {} from store {}", user, store);
        SID userRoot = SID.rootSID(user);
        ResultAndName result = null;
        String sfOrigName;
        try {
            sfOrigName = storeNames.getStoreDefaultName(new AeroDelegatedUserDevicePrincipal("polaris", user, DID.DUMMY), store);
        } catch (IOException e) {
            l.warn("could not get sf name from sparta", e);
            return;
        }

        // messy while loop because there's a race condition of an interleaved transaction
        // but we don't want to acquire a lock within a transaction
        while (result == null) {
            UniqueID parent = dbi.inTransaction((conn, status) -> conn.attach(MountPoints.class).getMountPointParent(userRoot, store));

            if (parent == null) {
                Lock l = objectStore.lockObject(userRoot);
                try {
                    result = dbi.inTransaction((conn, status) -> {
                        DAO dao = new DAO(conn);
                        UniqueID conflict = dao.children.getActiveChildNamed(userRoot, PolarisUtilities.stringToUTF8Bytes(sfOrigName));
                        String sfFinalName = conflict == null ? sfOrigName : nonConflictingName(dao, userRoot, store, sfOrigName);
                        if (dao.mountPoints.getMountPointParent(userRoot, store) == null) {
                            return new ResultAndName(objectStore.performTransform(dao,
                                    objectStore.checkAccessForStores(user, Sets.newHashSet(userRoot), Access.READ, Access.WRITE),
                                    DID.DUMMY, userRoot, new InsertChild(store, ObjectType.STORE, sfFinalName, null)), sfFinalName);
                        } else {
                            return null;
                        }
                    });
                } finally {
                    l.unlock();
                }
            } else {
                // something else has inserted the shared folder
                break;
            }
        }

        if (result != null) {
            result.result.updated.forEach(x -> notifier.notifyStoreUpdated(x.object.store, x.transformTimestamp));
            if (!sfOrigName.equals(result.name)) {
                try {
                    storeNames.setPersonalStoreName(new AeroDelegatedUserDevicePrincipal("polaris", user, DID.DUMMY), store, result.name);
                } catch (IOException e) {
                    l.warn("failed to set sf name through sparta after trans", e);
                }
            }
        }
    }


    private class ResultAndName {
        private OperationResult result;
        private String name;

        private ResultAndName(OperationResult result, String name)
        {
            this.result = result;
            this.name = name;
        }
    }

    // FIXME (RD) this method is taken from com.aerofs.lib.Util.java
    // since the scope of usage was small, i didn't think it was worth splitting lib into a separate module - clean up the dependency if this method is used more
    private static final Pattern NEXT_NAME_PATTERN =
            Pattern.compile("(.*)\\(([0-9]+)\\)$");

    private String nonConflictingName(DAO dao, UniqueID parent, UniqueID child, String fn)
    {
        int dot = fn.lastIndexOf(".");
        String extension = dot <= 0 ? "" : fn.substring(dot);
        String base = fn.substring(0, fn.length() - extension.length());

        // find the pattern of "(N)" at the end of the main part
        Matcher m = NEXT_NAME_PATTERN.matcher(base);
        String prefix;
        int num;
        if (m.find()) {
            prefix = m.group(1);
            try {
                num = Integer.valueOf(m.group(2)) + 1;
            } catch (NumberFormatException e) {
                // If the number can't be parsed because it's too large, it's probably not us who
                // generated that number. In this case, add a new number after it.
                prefix = base + " ";
                num = 2;
            }
        } else {
            prefix = base + " ";
            num = 2;
        }

        String newName = prefix + '(' + num + ')' + extension;
        UniqueID conflict = dao.children.getActiveChildNamed(parent, PolarisUtilities.stringToUTF8Bytes(newName));
        while (conflict != null && !conflict.equals(child)) {
            num++;
            newName = prefix + '(' + num + ')' + extension;
            conflict = dao.children.getActiveChildNamed(parent, PolarisUtilities.stringToUTF8Bytes(newName));
        }
        return newName;
    }
}
