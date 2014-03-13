/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.activity.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DID2User;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.*;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

public class HdGetActivities extends AbstractHdIMC<EIGetActivities>
{
    private static class Result
    {
        List<PBActivity> _activities;
        Set<DID> _unresolvedDIDs;
        Long _pageToken;
    }

    private final ActivityLog _al;
    private final DirectoryService _ds;
    private final DID2User _d2u;
    private final UserAndDeviceNames _udinfo;
    private final CfgLocalUser _cfgLocalUser;
    private final CfgLocalDID _cfgLocalDID;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public HdGetActivities(ActivityLog al, DirectoryService ds, DID2User d2u,
            UserAndDeviceNames udinfo, CfgLocalUser cfgLocalUser, CfgLocalDID cfgLocalDID,
            IMapSIndex2SID sidx2sid)
    {
        _al = al;
        _ds = ds;
        _d2u = d2u;
        _udinfo = udinfo;
        _cfgLocalUser = cfgLocalUser;
        _cfgLocalDID = cfgLocalDID;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EIGetActivities ev, Prio prio)
            throws Exception
    {
        Result res = getActivities_(ev._brief, ev._maxResults, ev._pageToken);
        ev.setResult_(res._activities, res._pageToken, !res._unresolvedDIDs.isEmpty());
    }

    /**
     * @param brief whether to use brief message formatting
     */
    private Result getActivities_(boolean brief, int maxResults, Long pageToken)
            throws SQLException, ExProtocolError
    {
        Result res = getActivitiesLocally_(brief, maxResults, pageToken);
        if (res._unresolvedDIDs.isEmpty()) return res;

        // There are unresolved devices. Retrieve their information from SP and then compose the
        // activities again.
        if (_udinfo.updateLocalDeviceInfo_(Lists.newArrayList(res._unresolvedDIDs))) {
            // try again only if the call to SP succeeded
            res = getActivitiesLocally_(brief, maxResults, pageToken);
            // Even though we've successfully retrieved information from SP, some devices can still
            // be left unresolved if SP didn't reveal their detail for privacy reasons. In this case
            // we would ignore these devices by setting the unresolvedDIDs list to empty.
            res._unresolvedDIDs = Collections.emptySet();
        }
        return res;
    }

    /**
     * Compose activity messages using information stored in the local database.
     */
    private Result getActivitiesLocally_(boolean brief, int maxResults, Long pageToken)
            throws SQLException
    {
        List<PBActivity> activities = Lists.newArrayList();
        Long newPageToken = null;
        SortedSet<DID> unresolvedDIDs = Sets.newTreeSet();

        long idxLast = pageToken == null ? Long.MAX_VALUE : pageToken;
        IDBIterator<ActivityRow> iter = _al.getActivites_(idxLast);
        try {
            int count = 0;
            while (iter.next_()) {
                ActivityRow ar = iter.get_();

                // hide activities for expelled stores
                if (_sidx2sid.getNullable_(ar._soid.sidx()) == null) continue;

                // outbound events are for auditing only and therefore hidden from the GUI
                if (ActivityLog.isOutbound(ar)) continue;

                PBActivity.Builder bd = PBActivity.newBuilder()
                        .setType(ar._type)
                        .setMessage(formatMessage(ar, brief, unresolvedDIDs))
                        .setTime(ar._time);

                // set the current path of the object. don't set path for trashed objects.
                OA oa = _ds.getAliasedOANullable_(ar._soid);
                if (oa != null && !_ds.isDeleted_(oa)) bd.setPath(_ds.resolve_(oa).toPB());

                activities.add(bd.build());

                if (++count >= maxResults) {
                    // Note that replyPageToken will be null if there is no more result before
                    // reaching maxResults.
                    newPageToken = ar._idx;
                    break;
                }
            }
        } finally {
            iter.close_();
        }

        Result res = new Result();
        res._activities = activities;
        res._unresolvedDIDs = unresolvedDIDs;
        res._pageToken = newPageToken;
        return res;
    }

    private static boolean and(StringBuilder sb, boolean first)
    {
        if (!first) sb.append(" and ");
        return false;
    }

    /**
     * Append a count and a noun to the given string builder, and apply the appropriate plurality to
     * the noun (i.e. add an 's' if appropriate).
     */
    private static void countAndNoun(StringBuilder sb, int count, String noun)
    {
        sb.append(count);
        sb.append(' ');
        sb.append(noun);
        if (count != 1) sb.append('s');
    }

    private StringBuilder buildUserListString(Set<DID> dids, boolean brief,
            /*out*/ Set<DID> unresolvedDIDs) throws SQLException
    {
        Set<DID> myDIDs = Sets.newTreeSet();
        Map<UserID, DID> user2did = Maps.newTreeMap();
        int unknownOwnerDevices = 0;
        for (DID did : dids) {
            if (did.equals(_cfgLocalDID.get())) {
                myDIDs.add(did);
                continue;
            }

            // resolve device ids to user ids
            UserID user = _d2u.getFromLocalNullable_(did);
            if (user == null) {
                unresolvedDIDs.add(did);
                unknownOwnerDevices++;
            } else if (user.equals(_cfgLocalUser.get())) {
                myDIDs.add(did);
            } else {
                user2did.put(user, did);
            }
        }

        StringBuilder sb = new StringBuilder();

        boolean first = true;
        if (!myDIDs.isEmpty()) {
            if (brief) {
                first = and(sb, first);
                sb.append("You");
            } else {
                boolean hasMoreDevices = !user2did.isEmpty() || unknownOwnerDevices > 0;
                sb.append(hasMoreDevices ? "You (on " : "You on ");
                int myUnknownDevices = 0;
                for (DID did : myDIDs) {
                    if (did.equals(_cfgLocalDID.get())) {
                        first = and(sb, first);
                        sb.append("this computer");
                        continue;
                    }

                    String name = _udinfo.getDeviceNameNullable_(did);
                    if (name == null) {
                        unresolvedDIDs.add(did);
                        myUnknownDevices++;
                    } else {
                        first = and(sb, first);
                        sb.append(name);
                    }
                }

                if (myUnknownDevices != 0) {
                    first = and(sb, first);
                    countAndNoun(sb, myUnknownDevices, "unknown device");
                }

                if (hasMoreDevices) sb.append(')');
            }
        }

        for (Entry<UserID, DID> en : user2did.entrySet()) {
            UserID user = en.getKey();
            // Guaranteed by the above code
            assert !user.equals(_cfgLocalUser.get());

            boolean wasFirst = first;
            first = and(sb, first);

            if (brief && !wasFirst) {
                sb.append("others");
                // skip printing of other users as well as other unknown devices
                return sb;
            }

            FullName fullName = _udinfo.getUserNameNullable_(user);
            if (fullName == null) {
                // use the user's email address if the user name is not found
                sb.append(user);
                // add the device to the unresolved list so the caller can try to retrieve the
                // owner's names through SP.GetDeviceInfo.
                unresolvedDIDs.add(en.getValue());
            } else {
                sb.append(brief ? fullName._first : fullName.getString());
            }
        }

        if (unknownOwnerDevices != 0) {
            // even though 'first' is not referred to after this assignment,
            // leave the assignment here in case references are added after
            // this line in the future.
            first = and(sb, first);
            countAndNoun(sb, unknownOwnerDevices, "unknown device");
        }

        return sb;
    }

    private String formatMessage(ActivityRow ar, boolean brief, /*out*/ Set<DID> unresolvedDIDs)
            throws SQLException
    {
        StringBuilder sb = buildUserListString(ar._dids, brief, unresolvedDIDs);

        sb.append(' ');

        boolean sameParent = false;
        boolean first = true;

        boolean creation = Util.test(ar._type, CREATION_VALUE);
        boolean modification = Util.test(ar._type, MODIFICATION_VALUE);
        boolean deletion = Util.test(ar._type, DELETION_VALUE);
        boolean movement = Util.test(ar._type, MOVEMENT_VALUE);

        modification = modification && !creation;
        movement = movement && !deletion;

        if (creation) {
            first = and(sb, first);
            sb.append("added");
        }

        if (modification) {
            first = and(sb, first);
            sb.append(S.MODIFIED);
        }

        if (deletion) {
            first = and(sb, first);
            sb.append("deleted");
        }

        if (movement) {
            // print movement activity last so it can be displayed close to the destination path.
            //
            // even though 'first' is not referred to after this assignment,
            // leave the assignment here in case references are added after
            // this line in the future.
            first = and(sb, first);

            assert ar._pathTo != null;
            // guaranteed by ActivityLog$committing()
            assert !ar._pathTo.equals(ar._path);
            assert !ar._pathTo.isEmpty();

            sameParent = ar._path.removeLast().equals(ar._pathTo.removeLast());
            sb.append(sameParent ? "renamed" : "moved");
        }

        sb.append(' ');

        // TODO (WW) connect the history of aliased objects to the target
        OA oa = _ds.getAliasedOANullable_(ar._soid);
        if (oa == null) sb.append("file or folder");
        else if (oa.isFile()) sb.append("file");
        else sb.append("folder");

        sb.append(' ');

        // show the destination paths for brief mode
        Path path = brief && movement ? ar._pathTo : ar._path;
        assert !path.isEmpty();
        sb.append(Util.quote(path.last()));

        if (!brief && movement) {
            assert ar._pathTo != null;

            sb.append(" to ");

            if (sameParent) {
                // the parents are the same
                sb.append(Util.quote(ar._pathTo.last()));
            } else {
                // the parents are different
                int len = ar._pathTo.elements().length;
                String parentName = len > 1 ? ar._pathTo.elements()[len - 2] : "";
                sb.append('\"');
                sb.append(parentName);
                sb.append(File.separatorChar);
                sb.append(ar._pathTo.last());
                sb.append('\"');
            }
        }

        return sb.toString();
    }
}
