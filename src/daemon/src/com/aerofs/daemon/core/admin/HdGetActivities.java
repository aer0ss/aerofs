/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DID2User;
import com.aerofs.daemon.event.admin.EIGetActivities;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.IUserAndDeviceNameDatabase;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.*;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private final IUserAndDeviceNameDatabase _udndb;
    private final TransManager _tm;
    private final CfgLocalUser _cfgLocalUser;
    private final CfgLocalDID _cfgLocalDID;
    private final SPBlockingClient.Factory _factSP;

    @Inject
    public HdGetActivities(ActivityLog al, DirectoryService ds, DID2User d2u,
            IUserAndDeviceNameDatabase udndb, TransManager tm, CfgLocalUser cfgLocalUser,
            CfgLocalDID cfgLocalDID, SPBlockingClient.Factory factSP)
    {
        _al = al;
        _ds = ds;
        _d2u = d2u;
        _udndb = udndb;
        _tm = tm;
        _cfgLocalUser = cfgLocalUser;
        _cfgLocalDID = cfgLocalDID;
        _factSP = factSP;
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

        // convert the set of devices into an ordered list so we can correspond the list returned
        // from SP with the supplied list.
        ArrayList<DID> dids = Lists.newArrayList(res._unresolvedDIDs);

        GetDeviceInfoReply reply;
        try {
            SPBlockingClient sp = _factSP.create_(SP.URL, _cfgLocalUser.get());
            sp.signInRemote();
            List<ByteString> pb = Lists.newArrayListWithExpectedSize(dids.size());
            for (DID did : dids) pb.add(did.toPB());
            reply = sp.getDeviceInfo(pb);
        } catch (Exception e) {
            Util.l(this).warn("ignored: " + Util.e(e, IOException.class));
            // Ignore and return with whatever we got from the previous call of getActivities_()..
            return res;
        }

        if (reply.getDeviceInfoCount() != dids.size()) {
            throw new ExProtocolError("server reply count mismatch (" +
                    reply.getDeviceInfoCount() + " != " + res._unresolvedDIDs.size() + ")");
        }

        Trans t = _tm.begin_();
        try {
            for (int i = 0; i < dids.size(); i++) {
                DID did = dids.get(i);
                // guaranteed by the above code
                assert i < reply.getDeviceInfoCount();
                PBDeviceInfo di = reply.getDeviceInfo(i);

                _udndb.setDeviceName_(did, di.hasDeviceName() ? di.getDeviceName() : null, t);
                if (di.hasOwner()) {
                    String user = di.getOwner().getUserEmail();
                    if (_d2u.getFromLocalNullable_(did) == null) _d2u.addToLocal_(did, user, t);
                    FullName fn = new FullName(di.getOwner().getFirstName(),
                            di.getOwner().getLastName());
                    _udndb.setUserName_(user, fn, t);
                }
            }
            t.commit_();
        } finally {
            t.end_();
        }

        res = getActivitiesLocally_(brief, maxResults, pageToken);
        // Even though we've successfully retrieved information from SP, some devices can still be
        // left unresolved if SP didn't reveal their detail for privacy reasons. In this case, we
        // would ignore these devices by setting the unresolvedDIDs list to empty.
        res._unresolvedDIDs = Collections.emptySet();
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

                PBActivity.Builder bd = PBActivity.newBuilder()
                        .setType(ar._type)
                        .setMessage(formatMessage(ar, brief, unresolvedDIDs))
                        .setTime(ar._time);

                // set the current path of the object. don't set path for trashed objects.
                OA oa = _ds.getAliasedOANullable_(ar._soid);
                if (oa != null) {
                    OID oid = oa.parent();
                    boolean trashed = false;
                    do {
                        if (oid.equals(OID.TRASH)) {
                            trashed = true;
                            break;
                        }
                        oid = _ds.getOA_(new SOID(ar._soid.sidx(), oid)).parent();
                    } while (!oid.isRoot());

                    if (!trashed) bd.setPath(_ds.resolve_(oa).toPB());
                }

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
        Map<String, DID> user2did = Maps.newTreeMap();
        int unknownOwnerDevices = 0;
        for (DID did : dids) {
            if (did.equals(_cfgLocalDID.get())) {
                myDIDs.add(did);
                continue;
            }

            // resolve device ids to user ids
            String user = _d2u.getFromLocalNullable_(did);
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
                boolean hasMoreDevices = !user2did.isEmpty() || unknownOwnerDevices != 0;
                sb.append(hasMoreDevices ? "You (on " : "You on ");
                int myUnknownDevices = 0;
                for (DID did : myDIDs) {
                    if (did.equals(_cfgLocalDID.get())) {
                        sb.append("this computer");
                        continue;
                    }

                    String name = _udndb.getDeviceNameNullable_(did);
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

        for (Entry<String, DID> en : user2did.entrySet()) {
            String user = en.getKey();
            // Guaranteed by the above code
            assert !user.equals(_cfgLocalUser.get());

            boolean wasFirst = first;
            first = and(sb, first);

            if (brief && !wasFirst) {
                sb.append("others");
                // skip printing of other users as well as other unknown devices
                return sb;
            }

            FullName fullName = _udndb.getUserNameNullable_(user);
            if (fullName == null) {
                // use the user's email address if the user name is not found
                sb.append(user);
                // add the device to the unresolved list so the caller can try to retrieve the
                // owner's names through SP.GetDeviceInfo.
                unresolvedDIDs.add(en.getValue());
            } else {
                sb.append(brief ? fullName._first : fullName.combine());
            }
        }

        if (unknownOwnerDevices != 0) {
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
            sb.append("modified");
        }

        if (deletion) {
            first = and(sb, first);
            sb.append("deleted");
        }

        if (movement) {
            // print movement activity last so it can be displayed close to the destination path
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
        sb.append(Util.q(path.last()));

        if (!brief && movement) {
            assert ar._pathTo != null;

            sb.append(" to ");

            if (sameParent) {
                // the parents are the same
                sb.append(Util.q(ar._pathTo.last()));
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
