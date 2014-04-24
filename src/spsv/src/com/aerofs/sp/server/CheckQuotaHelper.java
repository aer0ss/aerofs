/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CheckQuotaHelper
{
    private final UserQuotaUsageNotifier _userQuotaUsageNotifier;
    private final SharedFolder.Factory _factSharedFolder;

    CheckQuotaHelper(SharedFolder.Factory sharedFolderFactory, AsyncEmailSender emailSender,
            AuditClient auditClient, IDatabaseConnectionProvider<Connection> sqlTrans)
    {
        _factSharedFolder = sharedFolderFactory;
        _userQuotaUsageNotifier = new UserQuotaUsageNotifier(emailSender, auditClient);
    }

    public static Map<SID, Long> mapFromPBStoreUsageList(List<PBStoreUsage> storeUsages)
            throws ExBadArgs
    {
        Map<SID, Long> map = Maps.newHashMap();
        for (PBStoreUsage storeUsage : storeUsages) {
            SID sid = new SID(storeUsage.getSid());
            if (map.containsKey(sid)) throw new ExBadArgs("List contains duplicates");
            map.put(sid, storeUsage.getBytesUsed());
        }
        return map;
    }

    /**
     * Given the size of each store, notify users who are near the quota and return the set of
     * stores that should continue to collect content as described in
     * docs/design/team_server_quotas.md
     *
     * @param storeUsages Bytes used per store
     * @param quota Allowed usage per user (see design doc)
     * @return The stores that should collect content (see design doc)
     */
    public Set<SharedFolder> checkQuota(Map<SID, Long> storeUsages, @Nullable Long quota)
            throws SQLException, IOException, MessagingException, ExNotFound
    {
        if (quota == null) return sid2stores(storeUsages.keySet());

        // See docs/design/team_server_quotas.md for details
        Map<User, Long> usagePerUser = getUserUsages(storeUsages);
        persistUserUsagesAndNotify(usagePerUser, quota);
        Set<User> usersUnderQuota = usersUnderQuota(usagePerUser, quota);
        return getAllUsersStores(usersUnderQuota);
    }

    private Set<SharedFolder> getAllUsersStores(Set<User> users)
            throws SQLException
    {
        Set<SharedFolder> allStores = Sets.newHashSet();
        for (User user : users) {
            allStores.addAll(user.getSharedFolders());
        }
        return allStores;
    }

    private void persistUserUsagesAndNotify(Map<User, Long> usagePerUser, long quotaPerUser)
            throws MessagingException, SQLException, ExNotFound, IOException
    {
        for (Entry<User, Long> entry : usagePerUser.entrySet()) {
            _userQuotaUsageNotifier.updateUserBytesUsed(entry.getKey(), entry.getValue(),
                    quotaPerUser);
        }
    }

    private Set<User> usersUnderQuota(Map<User, Long> usagePerUser, long quotaPerUser)
    {
        Set<User> usersUnderQuota = Sets.newHashSet();

        for (Entry<User, Long> entry : usagePerUser.entrySet()) {
            if (entry.getValue() < quotaPerUser) {
                usersUnderQuota.add(entry.getKey());
            }
        }
        return usersUnderQuota;
    }

    private Map<User, Long> getUserUsages(Map<SID, Long> storeUsages)
            throws SQLException
    {
        Map<User, Long> usage = Maps.newHashMap();

        for (Entry<SID, Long> entry : storeUsages.entrySet()) {
            SharedFolder store = _factSharedFolder.create(entry.getKey());
            for (User user : store.getAllUsersExceptTeamServers()) {
                Long current = usage.get(user);
                if (current == null) current = 0L;
                current += entry.getValue();
                usage.put(user, current);
            }
        }
        return usage;
    }

    private Set<SharedFolder> sid2stores(Set<SID> sids)
    {
        Set<SharedFolder> all = Sets.newHashSet();
        for (SID sid : sids) {
            all.add(_factSharedFolder.create(sid));
        }
        return all;
    }
}
