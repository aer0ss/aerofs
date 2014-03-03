/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Collection;

/**
 * TODO: This is poor cohesion, a procedural class that exists only to avoid duplication btwn sp
 * and sparta. Euuuuugh.
 */
public class UserManagement
{
    private static Logger l = LoggerFactory.getLogger(UserManagement.class);

    /**
     * Perform all the bookkeeping needed after a device is unlinked.
     *
     * Send the CRL update to verkehr. Notify all the peer devices that it is time to refresh
     * their view of the CRLs. Clean up some sync status stuff.
     */
    public static void propagateDeviceUnlink(
            CommandDispatcher dispatcher,
            Collection<Device> peerDevices,
            ImmutableCollection<Long> revokedSerials)
            throws Exception
    {
        if (revokedSerials.isEmpty())
        {
            l.info("command verkehr, #serials: " + revokedSerials.size());
            ACLNotificationPublisher.verkehrFutureGet_(
                    dispatcher.getVerkehrAdmin().updateCRL(revokedSerials));

            for (Device peer : peerDevices) {
                dispatcher.enqueueCommand(peer.id(), CommandType.REFRESH_CRL);
            }
        }
    }

    // TODO: These two functions are ugly. But they beat the obvious alternatives.
    // What I really want is,
    //  deactivateUser( (throwIfNotSelfOrTSOf), caller, target, eraseDevices...
    //  deactivateUser( (throwIfNotSelfOrAdminOf), caller, target, eraseDevices...

    /**
     * Remove the user from the organization, unlink their devices, notify peers of unlink,
     * and update ACLs. All the bookkeeping related to removing one user.
     *
     * The caller is permitted by the fact they are a TS user.
     *
     * Note: this MUST be called in a SQL transaction context
     */
    public static void deactivateByTS(
            User caller, User target,
            boolean eraseDevices,
            CommandDispatcher dispatcher,
            ACLNotificationPublisher aclPublisher)
            throws Exception
    {
        if (!UserManagement.isSelfOrTSOf(caller, target)) throw new ExNoPerm("Insufficient permissions");
        deactivateUser(caller, target, eraseDevices, dispatcher, aclPublisher);
    }

    /**
     * Remove the user from the organization, unlink their devices, notify peers of unlink,
     * and update ACLs. All the bookkeeping related to removing one user.
     *
     * The caller is permitted by the fact they are an admin user.
     *
     * Note: this MUST be called in a SQL transaction context
     */
    public static void deactivateByAdmin(
            User caller, User target,
            boolean eraseDevices,
            CommandDispatcher dispatcher,
            ACLNotificationPublisher aclPublisher)
            throws Exception
    {
        if (! (caller.equals(target)|| caller.isAdminOf(target))) throw new ExNoPerm("Insufficient permissions");
        deactivateUser(caller, target, eraseDevices, dispatcher, aclPublisher);
    }

    /**
     * Remove the user from the organization, unlink their devices, notify peers of unlink,
     * and update ACLs. All the bookkeeping related to removing one user.
     *
     * Note: this MUST be called in a SQL transaction context
     */
    private static void deactivateUser(
            User caller, User target,
            boolean eraseDevices,
            CommandDispatcher dispatcher,
            ACLNotificationPublisher aclPublisher)
            throws Exception
    {
        // fetch device lists from target user before deactivation...
        Collection<Device> ownDevices = target.getDevices();
        Collection<Device> peerDevices = target.getPeerDevices();

        // deactivating the user gets us a list of affected certs _and_ affected users
        ImmutableSet.Builder<Long> bd = ImmutableSet.builder();
        Collection<UserID> affectedUsers = target.deactivate(bd, caller.equals(target) ? null : caller);
        ImmutableCollection<Long> revokedSerials = bd.build();

        // -- IMPORTANT: no DB writes beyond this point (allegedly) --

        // if the user _had_ any certified devices, unlink them:
        for (Device device : ownDevices) {
            dispatcher.replaceQueue(
                    device.id(),
                    eraseDevices ? CommandType.UNLINK_AND_WIPE_SELF : CommandType.UNLINK_SELF);
        }

        propagateDeviceUnlink(dispatcher, peerDevices, revokedSerials);

        // and finally, shared-folder impact of the user deletion:
        aclPublisher.publish_(affectedUsers);
    }

    public static boolean isSelfOrTSOf(User caller, User target)
            throws ExNotFound, SQLException
    {
        return caller.equals(target)
                || caller.id().equals(target.getOrganization().id().toTeamServerUserID());
    }
}
