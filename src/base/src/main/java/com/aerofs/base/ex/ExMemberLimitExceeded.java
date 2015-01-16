/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;

import static com.aerofs.base.BaseUtil.string2utf;
import static com.aerofs.base.BaseUtil.utf2string;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.newHashMap;

public class ExMemberLimitExceeded extends AbstractExWirable
{
    public final int _count;
    public final int _limit;

    private static final long serialVersionUID = -584715074847770115L;

    public ExMemberLimitExceeded(int count, int limit)
    {
        super(encodeData(count, limit));

        _count = count;
        _limit = limit;
    }

    private static byte[] encodeData(int count, int limit)
    {
        Map<String, Integer> data = newHashMap();

        data.put("count", count);
        data.put("limit", limit);

        return string2utf(new Gson().toJson(data));
    }

    public ExMemberLimitExceeded(PBException pb)
    {
        super(pb);

        checkNotNull(getDataNullable());

        JsonObject json = new Gson().fromJson(utf2string(getDataNullable()), JsonObject.class);

        _count = json.get("count").getAsInt();
        _limit = json.get("limit").getAsInt();
    }

    @Override
    public Type getWireType()
    {
        return Type.MEMBER_LIMIT_EXCEEDED;
    }
}
