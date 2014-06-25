/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.base.Preconditions;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

/**
 * Manage sending commands and verkehr messages for device actions.
 */
public class CommandDispatcher
{
    private VerkehrClient _verkehrClient;
    private JedisEpochCommandQueue _commandQueue;
    private JedisThreadLocalTransaction _jedisTrans;

    @Inject
    public CommandDispatcher(JedisEpochCommandQueue cmdQueue, JedisThreadLocalTransaction jedisTrans)
    {
        _commandQueue = cmdQueue;
        _jedisTrans = jedisTrans;
    }

    /**
     * Flush the command queue and then append the given command message.
     * After, send a verkehr message.
     * @param did               Targeted device
     * @param commandMessage    Command message
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    public void replaceQueue(DID did, String commandMessage)
            throws ExecutionException, InterruptedException
    {
        deliver(did, commandMessage, true);
    }

    /**
     * Add the given command message to the device command queue, and send a verkehr message.
     * @param did               Targeted device
     * @param commandMessage    Command message
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    public void enqueueCommand(DID did, String commandMessage)
            throws ExecutionException, InterruptedException
    {
        deliver(did, commandMessage, false);
    }

    /**
     * We get the Verkehr admin client after the SP service is already running for some reason
     */
    public CommandDispatcher setVerkehrClient(VerkehrClient verkehrClient)
    {
        _verkehrClient = verkehrClient;
        return this;
    }

    public VerkehrClient getVerkehrClient() { return _verkehrClient; }

    private void deliver(DID did, String commandMessage, boolean flushFirst)
            throws ExecutionException, InterruptedException
    {
        Preconditions.checkState(_verkehrClient != null);

        Epoch epoch;

        _jedisTrans.begin();
        try {
            if (flushFirst) _commandQueue.delete(did);
            epoch = _commandQueue.enqueue(did, commandMessage);
            _jedisTrans.commit();
        } finally {
            // always clean up because cleanup is idempotent and results in the same state as commit
            _jedisTrans.cleanUp();
        }

        assert epoch != null;

        Command command = CommandUtil.createCommandFromMessage(commandMessage, epoch.get());
        _verkehrClient.publish(Topics.getCMDTopic(did.toStringFormal(), true), command.toByteArray()).get();
    }
}
