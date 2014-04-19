/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CheckQuotaHelper
{
    private final SharedFolder.Factory _factSharedFolder;

    CheckQuotaHelper(SharedFolder.Factory sharedFolderFactory)
    {
        _factSharedFolder = sharedFolderFactory;
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

    public Set<SharedFolder> getStoresThatShouldCollectContent(Map<SID, Long> storeUsages,
            @Nullable Long quota)
            throws SQLException
    {
        if (quota == null) return allStores(storeUsages);

        // See docs/design/team_server_quotas.md for details
        Map<User, Long> usagePerUser = getUserUsages(storeUsages);
        Set<User> usersUnderQuota = getUsersUnderQuota(usagePerUser, quota);
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

    private Set<User> getUsersUnderQuota(Map<User, Long> usagePerUser, long quotaPerUser)
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

    private Set<SharedFolder> allStores(Map<SID, Long> storeUsages)
    {
        Set<SharedFolder> all = Sets.newHashSet();
        for (SID sid : storeUsages.keySet()) {
            all.add(_factSharedFolder.create(sid));
        }
        return all;
    }
}
