package com.aerofs.ssmp;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.ids.DID;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SSMPIdentifiers
{
    public static SSMPIdentifier getACLTopic(String userId)
    {
        // SSMP id length is limited to 64 characters
        // email address can be up to 254 characters
        // use the base64-encoded sha256 hash of the userid as the topic to fit in 64 chars with low
        // likelihood of collision and some room left for the topic prefix
        // NB: collisions wouldn't be a huge deal, at worst they would result in a few unnecessary
        // calls to SP/Sparta
        return SSMPIdentifier.fromInternal("acl/" + Base64.getEncoder().encodeToString(
                BaseSecUtil.hash(userId.getBytes(StandardCharsets.UTF_8))));
    }

    public static SSMPIdentifier getCMDUser(DID did)
    {
        return SSMPIdentifier.fromInternal(did.toStringFormal() + "/cmd");
    }
}
