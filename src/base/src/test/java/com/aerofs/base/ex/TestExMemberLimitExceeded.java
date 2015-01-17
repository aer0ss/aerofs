/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.util.Map;

import static com.aerofs.base.BaseUtil.string2utf;
import static com.google.common.collect.Maps.newHashMap;
import static org.junit.Assert.assertEquals;

public class TestExMemberLimitExceeded
{
    @Test
    public void shouldCorrectlyDecodeConstructedException()
            throws Exception
    {
        // the purpose of this test is to ensure the consumers of this exception can decode
        // the exception produced by the producer when the exception is sent over the wire
        ExMemberLimitExceeded src = new ExMemberLimitExceeded(5, 3);
        PBException pb = Exceptions.toPB(src);
        ExMemberLimitExceeded unwired = new ExMemberLimitExceeded(pb);

        assertEquals(5, unwired._count);
        assertEquals(3, unwired._limit);
    }

    @Test
    public void shouldCorrectlyDecodeDataOverTheWire()
            throws Exception
    {
        // the purpose of this test is to ensure the producer of this exception will conform
        // to the format expected by the old clients.
        PBException pb = PBException.newBuilder()
                .setType(Type.MEMBER_LIMIT_EXCEEDED)
                .setData(ByteString.copyFrom(encodeData(5, 3)))
                .build();

        ExMemberLimitExceeded e = new ExMemberLimitExceeded(pb);

        assertEquals(5, e._count);
        assertEquals(3, e._limit);
    }

    // N.B. _do not_ change this implementation as old clients will be expecting this format.
    private static byte[] encodeData(int count, int limit)
    {
        Map<String, Integer> data = newHashMap();

        data.put("count", count);
        data.put("limit", limit);

        return string2utf(new Gson().toJson(data));
    }
}
