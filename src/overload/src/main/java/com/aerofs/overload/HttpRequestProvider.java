package com.aerofs.overload;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpRequest;

public interface HttpRequestProvider {

    /**
     * IMPORTANT: it is the *implementer's* responsibility to release the buffer
     * if the implementation throws. Responsibility for buffer cleanup ends once {@code FullHttpRequest}
     * is constructed and the method returns.
     */
    FullHttpRequest getRequest(ByteBufAllocator allocator) throws Exception;
}
