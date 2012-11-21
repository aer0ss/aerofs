/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.lib.ContentHash;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class BlockUtil
{
    public static int getNumBlocks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        return bytes.length / ContentHash.UNIT_LENGTH;
    }

    public static boolean isOneBlock(ContentHash hash)
    {
        return getNumBlocks(hash) == 1;
    }

    /**
     * ContentHash for files spanning multible blocks are simply the concatenation of the hashes
     * of each block. This method extract the hash for a given block from the concatenated hash.
     */
    public static ContentHash getBlock(ContentHash hash, int i)
    {
        return new ContentHash(
                Arrays.copyOfRange(hash.getBytes(), i * ContentHash.UNIT_LENGTH, (i + 1) *
                        ContentHash.UNIT_LENGTH));
    }

    /**
     * ContentHash for files spanning multible blocks are simply the concatenation of the hashes
     * of each block. This method splits a concatenated hash into a list of hashes for all the
     * blocks
     */
    public static List<ContentHash> splitBlocks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        int numBlocks = getNumBlocks(hash);
        List<ContentHash> list = Lists.newArrayListWithCapacity(numBlocks);
        for (int i = 0; i < numBlocks; ++i) {
            list.add(new ContentHash(
                    Arrays.copyOfRange(bytes, i * ContentHash.UNIT_LENGTH, (i + 1) *
                            ContentHash.UNIT_LENGTH)));
        }
        return list;
    }
}
