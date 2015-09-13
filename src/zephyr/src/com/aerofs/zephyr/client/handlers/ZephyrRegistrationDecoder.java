package com.aerofs.zephyr.client.handlers;

import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.util.Arrays;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_PAYLOAD_LEN;

final class ZephyrRegistrationDecoder extends FrameDecoder
{
    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception
    {
        if (buffer.readableBytes() < ZEPHYR_REG_MSG_LEN) { // want entire message
            return null;
        }

        byte[] zephyrProtocolMagic = new byte[ZEPHYR_MAGIC.length];
        buffer.readBytes(zephyrProtocolMagic);
        if (!Arrays.equals(zephyrProtocolMagic, ZEPHYR_MAGIC)) {
            throw new ExBadZephyrMessage("bad zephyr protocol message:" + Arrays.toString(zephyrProtocolMagic));
        }

        int payloadLength = buffer.readInt();
        if (payloadLength != ZEPHYR_REG_PAYLOAD_LEN) {
            throw new ExBadZephyrMessage("bad reg len exp:" + ZEPHYR_REG_PAYLOAD_LEN + " act:" + buffer.readableBytes());
        }

        int assignedZid = buffer.readInt();

        ctx.getChannel().getPipeline().remove(this);
        Channels.fireMessageReceived(channel, new Registration(assignedZid));
        if (!buffer.readable()) return null;

        ChannelBuffer r = buffer.duplicate();
        buffer.skipBytes(buffer.readableBytes());
        return r;
    }
}
