package com.aerofs.zephyr.client.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.slf4j.Logger;

import java.util.Arrays;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_PAYLOAD_LEN;

final class ZephyrRegistrationDecoder extends FrameDecoder
{
    private static final Logger l = Loggers.getLogger(ZephyrRegistrationDecoder.class);

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

        return new Registration(assignedZid);
    }
}
