/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExInvalidZephyrMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrDataMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrRegistrationMessage;
import com.aerofs.lib.Param;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.util.Arrays;

public class ZephyrFrameDecoder extends FrameDecoder
{

    private boolean _isHeaderDone = false;
    private boolean _clientMessage = true;
    private int _payloadLength = -1;

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception
    {
        // Made the assumption that both client and server headers are the
        // same size
        assert ZephyrConstants.ZEPHYR_CLIENT_HDR_LEN == ZephyrConstants.ZEPHYR_SERVER_HDR_LEN;

        if (!_isHeaderDone) {
            if (buffer.readableBytes() < ZephyrConstants.ZEPHYR_SERVER_HDR_LEN) {
                // The entire header must be available in order to process
                // the payload.
                return null;
            }

            // Mark this position in the buffer in case the entire payload isn't
            // available and the buffer must be reset here
            buffer.markReaderIndex();

            boolean clientMessage = true;

            // Check that this message's magic value matches the client magic
            int magic = buffer.readInt();
            if (Param.CORE_MAGIC != magic) {
                // The magic value does not match the client magic, so check if it
                // matches the server magic value
                buffer.resetReaderIndex();

                byte[] server_magic = new byte[ZephyrConstants.ZEPHYR_MAGIC.length];
                buffer.readBytes(server_magic);
                if (!Arrays.equals(server_magic, ZephyrConstants.ZEPHYR_MAGIC)) {
                    // The magic values differ, meaning that
                    // These clients may be incompatible
                    throw new ExInvalidZephyrMessage("Core magic values don't match");
                }

                clientMessage = false;
            }

            // Extract the length of the payload
            _payloadLength = buffer.readInt();

            // Ensure the payload length is a sane value
            if (_payloadLength <= 0) {
                throw new ExInvalidZephyrMessage("Invalid payload length");
            }

            // Mark that we're done reading the header
            _isHeaderDone = true;
            _clientMessage = clientMessage;
            buffer.markReaderIndex();
        }

        if (buffer.readableBytes() < _payloadLength) {
            // Reset the buffer to before the header for reading
            // next time more data arrives
            buffer.resetReaderIndex();
            return null;
        }

        // Reset state for next packet
        _isHeaderDone = false;

        ChannelBuffer payload = buffer.readBytes(_payloadLength);
        return _clientMessage ? new ZephyrDataMessage(payload) : ZephyrRegistrationMessage.create(
                payload);
    }

}
