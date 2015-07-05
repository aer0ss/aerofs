/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.SSMPIdentifiers;
import com.aerofs.ids.DID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPRequest;
import com.google.common.base.Preconditions;

import javax.inject.Inject;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

/**
 * Manage sending commands and SSMP messages for device actions.
 */
public class CommandDispatcher
{
    private SSMPConnection _ssmp;
    private JedisEpochCommandQueue _commandQueue;
    private JedisThreadLocalTransaction _jedisTrans;

    @Inject
    public CommandDispatcher(JedisEpochCommandQueue cmdQueue, JedisThreadLocalTransaction jedisTrans,
                             SSMPConnection ssmp)
    {
        _commandQueue = cmdQueue;
        _jedisTrans = jedisTrans;
        _ssmp = ssmp;
    }

    /**
     * Flush the command queue and then append the given command message.
     * After, send a SSMP message.
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
     * Add the given command message to the device command queue, and send a SSMP message.
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

    private void deliver(DID did, String commandMessage, boolean flushFirst)
            throws ExecutionException, InterruptedException
    {
        Preconditions.checkState(_ssmp != null);

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
        _ssmp.request(SSMPRequest.ucast(SSMPIdentifiers.getCMDUser(did),
                Base64.getEncoder().encodeToString(command.toByteArray()))).get();
    }
}
