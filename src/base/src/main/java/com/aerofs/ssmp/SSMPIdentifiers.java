package com.aerofs.ssmp;

import com.aerofs.ids.DID;

import java.nio.charset.StandardCharsets;

public class SSMPIdentifiers
{
    public static SSMPIdentifier getACLTopic(String userId)
    {
        return SSMPIdentifier.fromInternal("acl/" + java.util.Base64.getEncoder().encodeToString(
                userId.getBytes(StandardCharsets.UTF_8)));
    }

    public static SSMPIdentifier getCMDUser(DID did)
    {
        return SSMPIdentifier.fromInternal(did.toStringFormal() + "/cmd");
    }
}
