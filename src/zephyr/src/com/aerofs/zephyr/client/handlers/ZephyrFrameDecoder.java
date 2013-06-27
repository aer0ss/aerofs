package com.aerofs.zephyr.client.handlers;

import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.util.Arrays;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_CLIENT_HDR_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_PAYLOAD_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_SERVER_HDR_LEN;
import static com.aerofs.lib.LibParam.CORE_MAGIC;
import static com.google.common.base.Preconditions.checkState;

final class ZephyrFrameDecoder extends FrameDecoder
{
    private boolean receivedHeader = false;
    private boolean relayedData = true;
    private int payloadLength = -1;

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception
    {
        checkState(ZEPHYR_CLIENT_HDR_LEN == ZEPHYR_SERVER_HDR_LEN);

        if (!receivedHeader) {
            if (buffer.readableBytes() < ZEPHYR_SERVER_HDR_LEN) { // need entire header to process payload
                return null;
            }

            // Mark this position in the buffer in case the entire payload isn't
            // available and the buffer must be reset here
            buffer.markReaderIndex();

            boolean isRelayedData = true;
            int clientMagic = buffer.readInt();
            if (CORE_MAGIC != clientMagic) { // doesn't look like relayed data - check if it's a protocol message
                buffer.resetReaderIndex();

                byte[] zephyrProtocolMagic = new byte[ZEPHYR_MAGIC.length];
                buffer.readBytes(zephyrProtocolMagic);
                if (!Arrays.equals(zephyrProtocolMagic, ZEPHYR_MAGIC)) {
                    throw new ExBadZephyrMessage("not relayed data or protocol message b:" + Arrays.toString(zephyrProtocolMagic));
                }

                isRelayedData = false;
            }

            payloadLength = buffer.readInt();
            if (payloadLength <= 0) {
                throw new ExBadZephyrMessage("bad payload len:" + payloadLength);
            }

            receivedHeader = true;
            relayedData = isRelayedData;
            buffer.markReaderIndex();
        }

        // FIXME (AG): see if there's a way to avoid reading the same part of the packet multiple times

        if (buffer.readableBytes() < payloadLength) {
            buffer.resetReaderIndex(); // reset buffer position to before the header for reading for when we next receive data
            return null;
        }

        // reset state for the next incoming bytes

        receivedHeader = false;

        // IMPORTANT: at this point we've stripped off the header for the protocol packets!

        ChannelBuffer payload = buffer.readBytes(payloadLength);
        if (relayedData) {
            return payload;
        } else {
            return newRegistration(payload);
        }
    }

    private Registration newRegistration(ChannelBuffer buffer)
            throws ExBadZephyrMessage
    {
        if (buffer.readableBytes() < ZEPHYR_REG_PAYLOAD_LEN) {
            throw new ExBadZephyrMessage("bad reg len exp:" + ZEPHYR_REG_PAYLOAD_LEN + " act:" + buffer.readableBytes());
        }

        return new Registration(buffer.readInt());
    }
}
