/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.organization;

import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.ListSharedFoldersResponse.PBSharedFolder;
import com.aerofs.sp.server.lib.organization.IOrganizationDatabase;
import com.aerofs.sp.server.lib.organization.IOrganizationDatabase.SharedFolder;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.common.collect.Lists;

import java.sql.SQLException;
import java.util.List;

/**
 * TODO (WW) move stuff in this class to Organization
 *
 * Wrapper class for organization-related DB queries
 */
public class OrganizationManagement
{
    private static final int DEFAULT_MAX_RESULTS = 100;
    // To avoid DoS attacks forbid listSharedFolders queries exceeding 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    private final IOrganizationDatabase _db;

    public OrganizationManagement(IOrganizationDatabase db)
    {
        _db = db;
    }

    private static Integer sanitizeMaxResults(Integer maxResults)
    {
        if (maxResults == null) return DEFAULT_MAX_RESULTS;
        else if (maxResults > ABSOLUTE_MAX_RESULTS) return ABSOLUTE_MAX_RESULTS;
        else if (maxResults < 0) return 0;

        else return maxResults;
    }

    public List<PBSharedFolder> listSharedFolders(OrgID orgId, Integer maxResults, Integer offset)
            throws SQLException
    {
        if (offset == null) offset = 0;
        assert offset >= 0;

        List<SharedFolder> sfs = _db.listSharedFolders(orgId, sanitizeMaxResults(maxResults),
                offset);

        List<PBSharedFolder> pbs = Lists.newArrayListWithCapacity(sfs.size());
        for (SharedFolder sf : sfs) {
            List<PBSubjectRolePair> pbsrps = Lists.newArrayListWithCapacity(sf._acl.size());
            for (SubjectRolePair srp : sf._acl) {
                pbsrps.add(PBSubjectRolePair.newBuilder()
                        .setSubject(srp._subject.toString())
                        .setRole(srp._role.toPB())
                        .build());
            }

            pbs.add(PBSharedFolder.newBuilder()
                    .setStoreId(sf._sid.toPB())
                    .setName(sf._name)
                    .addAllSubjectRole(pbsrps)
                    .build());
        }
        return pbs;
    }

    public int countSharedFolders(OrgID orgId)
        throws SQLException
    {
        return _db.countSharedFolders(orgId);
    }
}
