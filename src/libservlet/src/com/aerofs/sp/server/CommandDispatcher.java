/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.google.common.base.Preconditions;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

/**
 * Manage sending commands and verkehr messages for device actions.
 */
public class CommandDispatcher
{
    private VerkehrAdmin _verkehrAdmin;
    private JedisEpochCommandQueue _commandQueue;
    private JedisThreadLocalTransaction _jedisTrans;

    @Inject
    public CommandDispatcher(JedisEpochCommandQueue cmdQueue, JedisThreadLocalTransaction jedisTrans)
    {
        _commandQueue = cmdQueue;
        _jedisTrans = jedisTrans;
    }

    /**
     * Flush the command queue and then append the given command type.
     * After, send a verkehr message.
     * @param did   Targeted device
     * @param cmd   Command type
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    public void replaceQueue(DID did, CommandType cmd)
            throws ExecutionException, InterruptedException
    {
        deliver(did, cmd, true);
    }

    /**
     * Add the given command to the device command queue, and send a verkehr message.
     * @param did   Targeted device
     * @param cmd   Command type
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    public void enqueueCommand(DID did, CommandType cmd)
            throws ExecutionException, InterruptedException
    {
        deliver(did, cmd, false);
    }

    /**
     * We get the Verkehr admin client after the SP service is already running for some reason
     */
    public CommandDispatcher setAdminClient(VerkehrAdmin adminClient)
    {
        _verkehrAdmin = adminClient;
        return this;
    }

    public VerkehrAdmin getVerkehrAdmin() { return _verkehrAdmin; }

    private void deliver(DID did, CommandType cmd, boolean flushFirst)
            throws ExecutionException, InterruptedException
    {
        Preconditions.checkState(_verkehrAdmin != null);

        _jedisTrans.begin();
        if (flushFirst) _commandQueue.delete(did);
        Epoch epoch = _commandQueue.enqueue(did, cmd);
        _jedisTrans.commit();
        assert epoch != null;

        Command command = Command.newBuilder()
                .setEpoch(epoch.get())
                .setType(cmd)
                .build();
        _verkehrAdmin.deliverPayload_(did.toStringFormal(), command.toByteArray()).get();
    }
}