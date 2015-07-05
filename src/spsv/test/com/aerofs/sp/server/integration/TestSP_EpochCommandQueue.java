/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Sp.AckCommandQueueHeadReply;
import com.aerofs.proto.Sp.GetCommandQueueHeadReply;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Base64;

public class TestSP_EpochCommandQueue extends AbstractSPTest
{
    // Device that we will use for testing.
    private Device _device;

    //
    // Utils
    //

    private void updateUserName()
            throws Exception
    {
        service.setUserPreferences(session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .id()
                .getString(),
                "New First", "New Last", null, null);
    }

    private void updateDeviceName()
            throws Exception
    {
        service.setUserPreferences(session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .id()
                .getString(),
                null, null, BaseUtil.toPB(_device.id()), "New Test Device");
    }

    private void updateUserAndDeviceName()
            throws Exception
    {
        service.setUserPreferences(session
                .getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                .id()
                .getString(),
                "New First", "New Last", BaseUtil.toPB(_device.id()), "New Test Device");
    }

    //
    // Fast Path Tests
    //

    @Before
    public void setupTestSPEpochCommandQueue()
            throws Exception
    {
        sqlTrans.begin();

        _device = factDevice.create(new DID(UniqueID.generate()));

        User user = factUser.create(UserID.fromExternal("something@email.com"));
        user.save(new byte[0], new FullName("First", "Last"));
        _device.save(user, "", "", "Test Device");

        sqlTrans.commit();

        setSession(user);
    }

    @Test
    public void shouldSendMessageWhenUserNameUpdated()
            throws Exception
    {
        updateUserName();

        Assert.assertEquals(1, getPublishedMessages().size());
        Assert.assertEquals(CommandType.INVALIDATE_USER_NAME_CACHE, getCommandFromPublishedBytes().getType());
    }

    @Test
    public void shouldSendMessageWhenDeviceNameUpdated()
            throws Exception
    {
        updateDeviceName();

        Assert.assertEquals(1, getPublishedMessages().size());
        Assert.assertEquals(CommandType.INVALIDATE_DEVICE_NAME_CACHE,
                getCommandFromPublishedBytes().getType());
    }

    private Command getCommandFromPublishedBytes()
            throws InvalidProtocolBufferException
    {
        Published published = getPublishedMessages().get(0);
        return Command.parseFrom(Base64.getDecoder().decode(published.bytes));
    }

    @Test
    public void shouldSendTwoMessagesWhenUserAndDeviceNameUpdated()
            throws Exception
    {
        updateUserAndDeviceName();
        Assert.assertEquals(2, getPublishedMessages().size());
    }

    //
    // Get command queue head and ack command queue head tests.
    //

    @Test
    public void shouldGiveCorrectCommandQueueHeadAfterUserNameUpdate()
            throws Exception
    {
        updateUserName();
        GetCommandQueueHeadReply reply = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();

        Assert.assertTrue(reply.hasCommand());
        Assert.assertEquals(CommandType.INVALIDATE_USER_NAME_CACHE, reply.getCommand().getType());
    }

    @Test
    public void shouldGiveCorrectCommandQueueHeadAfterDeviceNameUpdate()
            throws Exception
    {
        updateDeviceName();
        GetCommandQueueHeadReply reply = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();

        Assert.assertTrue(reply.hasCommand());
        Assert.assertEquals(CommandType.INVALIDATE_DEVICE_NAME_CACHE, reply.getCommand().getType());
    }

    @Test
    public void shouldEmptySizeOneQueueWhenAckedCorrectlyWithoutErrorFlag()
            throws Exception
    {
        updateDeviceName();
        GetCommandQueueHeadReply headReply;
        AckCommandQueueHeadReply ackReply;

        headReply = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();
        ackReply = service.ackCommandQueueHead(BaseUtil.toPB(_device.id()),
                headReply.getCommand().getEpoch(), false).get();
        headReply = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();

        Assert.assertFalse(ackReply.hasCommand());
        Assert.assertFalse(headReply.hasCommand());
    }

    @Test
    public void shouldNotEmptySizeOneQueueWhenAckedWithErrorFlag()
            throws Exception
    {
        updateDeviceName();
        GetCommandQueueHeadReply headReply1, headReply2;
        AckCommandQueueHeadReply ackReply;

        headReply1 = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();
        ackReply = service.ackCommandQueueHead(BaseUtil.toPB(_device.id()),
                headReply1.getCommand().getEpoch(), true).get();
        headReply2 = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();

        Assert.assertTrue(ackReply.hasCommand());
        Assert.assertEquals(CommandType.INVALIDATE_DEVICE_NAME_CACHE, ackReply.getCommand().getType());

        Assert.assertTrue(headReply1.hasCommand());
        Assert.assertEquals(CommandType.INVALIDATE_DEVICE_NAME_CACHE, headReply1.getCommand().getType());

        Assert.assertTrue(headReply2.hasCommand());
        Assert.assertEquals(CommandType.INVALIDATE_DEVICE_NAME_CACHE, headReply2.getCommand().getType());

        // The new epoch number should be higher.
        Assert.assertTrue(headReply1.getCommand().getEpoch() < headReply2.getCommand().getEpoch());
    }

    @Test
    public void shouldEnqueueTwoCommandWhenBothUserAndDeviceNameAreUpdated()
            throws Exception
    {
        updateUserAndDeviceName();

        GetCommandQueueHeadReply headReply;
        AckCommandQueueHeadReply ackReply;

        // Get the head of the command queue (the first command).
        headReply = service.getCommandQueueHead(BaseUtil.toPB(_device.id())).get();
        // Process first command...

        ackReply = service.ackCommandQueueHead(BaseUtil.toPB(_device.id()),
                headReply.getCommand().getEpoch(), false).get();

        // Process the second command...
        Assert.assertTrue(headReply.hasCommand());
        Assert.assertTrue(ackReply.hasCommand());

        // Ack the second command.
        ackReply = service.ackCommandQueueHead(BaseUtil.toPB(_device.id()),
                ackReply.getCommand().getEpoch(), false).get();

        // And make sure no command are left.
        Assert.assertFalse(ackReply.hasCommand());
    }
}
