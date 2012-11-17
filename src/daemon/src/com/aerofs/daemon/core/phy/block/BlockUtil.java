/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class BlockUtil
{
    static final int BLOCK_HASH_SIZE = ContentHash.UNIT_LENGTH;
    static final long FILE_BLOCK_SIZE = Param.FILE_CHUNK_SIZE;

    static int getNumBlocks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        return bytes.length / BLOCK_HASH_SIZE;
    }

    static boolean isOneBlock(ContentHash hash)
    {
        return getNumBlocks(hash) == 1;
    }

    static ContentHash getBlock(ContentHash hash, int i)
    {
        return new ContentHash(
                Arrays.copyOfRange(hash.getBytes(), i * BLOCK_HASH_SIZE, (i + 1) * BLOCK_HASH_SIZE));
    }

    static List<ContentHash> splitBlocks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        int numBlocks = getNumBlocks(hash);
        List<ContentHash> list = Lists.newArrayListWithCapacity(numBlocks);
        for (int i = 0; i < numBlocks; ++i) {
            list.add(new ContentHash(
                    Arrays.copyOfRange(bytes, i * BLOCK_HASH_SIZE, (i + 1) * BLOCK_HASH_SIZE)));
        }
        return list;
    }
}
