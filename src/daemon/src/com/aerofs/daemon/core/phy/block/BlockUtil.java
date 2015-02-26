/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.lib.ContentBlockHash;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class BlockUtil
{
    public static int getNumBlocks(ContentBlockHash hash)
    {
        byte[] bytes = hash.getBytes();
        return bytes.length / ContentBlockHash.UNIT_LENGTH;
    }

    public static boolean isOneBlock(ContentBlockHash hash)
    {
        return getNumBlocks(hash) == 1;
    }

    /**
     * ContentHash for files spanning multiple blocks are simply the concatenation of the hashes
     * of each block. This method extract the hash for a given block from the concatenated hash.
     */
    public static ContentBlockHash getBlock(ContentBlockHash hash, int i)
    {
        return new ContentBlockHash(
                Arrays.copyOfRange(hash.getBytes(), i * ContentBlockHash.UNIT_LENGTH, (i + 1) *
                        ContentBlockHash.UNIT_LENGTH));
    }

    /**
     * ContentHash for files spanning multible blocks are simply the concatenation of the hashes
     * of each block. This method splits a concatenated hash into a list of hashes for all the
     * blocks
     */
    public static List<ContentBlockHash> splitBlocks(ContentBlockHash hash)
    {
        byte[] bytes = hash.getBytes();
        int numBlocks = getNumBlocks(hash);
        List<ContentBlockHash> list = Lists.newArrayListWithCapacity(numBlocks);
        for (int i = 0; i < numBlocks; ++i) {
            list.add(new ContentBlockHash(
                    Arrays.copyOfRange(bytes, i * ContentBlockHash.UNIT_LENGTH, (i + 1) *
                            ContentBlockHash.UNIT_LENGTH)));
        }
        return list;
    }
}
