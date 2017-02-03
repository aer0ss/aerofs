/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.daemon.core.phy.DigestSerializer;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;

import javax.annotation.Nonnull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Arrays;

import static com.aerofs.daemon.core.phy.PrefixOutputStream.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Prefixes storage scheme:
 *
 * auxroot/
 *      p/
 *          <sidx>/
 *              <oid>/
 *                  <kidx>[-<scope>]/
 *                      blocks                  incremental ContentBlockHash
 *                      hash                    incremental hash state of whole prefix
 *                      _                       prefix tail
 *                      _.hash                  incremental hash state of prefix tail
 *                      d/
 *                          <sha256>            completed block
 *
 * A prefix is considered invalid and discarded if the size of the prefix and the
 * incremental hash do not match.
 *
 * TODO: speculative chunk upload in background?
 *  -> nice to have but fairly complex and probably overkill for now
 */
class BlockPrefix implements IPhysicalPrefix
{
    final SOKID _sokid;
    final InjectableFile _f;

    private final static String BLOCK_HASH = "blocks";
    private final static String HASH = "hash";
    private final static String TAIL = "_";
    private final static String BLOCKS = "d";

    BlockPrefix(SOKID sokid, InjectableFile f)
    {
        _sokid = sokid;
        _f = f;
    }

    @Override
    public long getLength_() {
        try {
            return length();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public byte[] hashState_()
    {
        try {
            long prefixLength = length();
            if (prefixLength == 0) return null;
            return DigestSerializer.serialize(
                    DigestSerializer.deserialize(_f.newChild(HASH).toByteArray(), prefixLength));
        } catch (Exception e) {
            BlockStorage.l.warn("failed to reload hash for {}", _f, e);
            _f.deleteIgnoreErrorRecursively();
            return null;
        }
    }

    long length() throws IOException {
        long blocksLength = _f.newChild(BLOCK_HASH).lengthOrZeroIfNotFile();
        if (blocksLength % ContentBlockHash.UNIT_LENGTH != 0) {
            throw new IOException("invalid prefix blocks " + _sokid + " " + blocksLength);
        }
        long tailLength = _f.newChild(TAIL).lengthOrZeroIfNotFile();
        if (tailLength > ClientParam.FILE_BLOCK_SIZE) {
            throw new IOException("invalid prefix tail " + _sokid + " " + tailLength);
        }
        return (blocksLength / ContentBlockHash.UNIT_LENGTH) * ClientParam.FILE_BLOCK_SIZE
                + tailLength;
    }

    @Override
    public PrefixOutputStream newOutputStream_(boolean append) throws IOException
    {
        if (!append) _f.deleteOrThrowIfExistRecursively();
        _f.ensureDirExists();
        try {
            long prefixLength = length();
            final MessageDigest md = prefixLength > 0
                    ? DigestSerializer.deserialize(_f.newChild(HASH).toByteArray(), prefixLength)
                    : BaseSecUtil.newMessageDigest();
            ChunkingOutputStream cos = new ChunkingOutputStream(md);
            PrefixOutputStream pos = new PrefixOutputStream(cos, md);
            cos.pos = pos;
            return pos;
        } catch (IllegalArgumentException|IOException e) {
            BlockStorage.l.warn("failed to reload hash for {}", _f, e);
            _f.deleteIgnoreErrorRecursively();
            if (e instanceof IllegalArgumentException) throw new IOException("corrupted prefix");
            throw e;
        }
    }

    @FunctionalInterface
    interface BlockConsumer
    {
        void consume(ContentBlockHash h, InjectableFile f) throws SQLException, IOException;
    }

    /**
     * Move all chunks of the prefix to a directory from which:
     *      - they will eventually be committed to the storage backend
     *      - they can be read to service reads until then
     *
     * rollback if transaction is aborted
     */
    public void consumeChunks_(BlockConsumer consumer) throws SQLException, IOException {
        InjectableFile blockDir = _f.newChild(BLOCKS);
        String[] complete = blockDir.list();
        if (complete != null) {
            for (String c : complete) {
                ContentBlockHash h;
                try {
                    h = new ContentBlockHash(BaseUtil.hexDecode(c));
                } catch (ExFormatError e) { continue; }
                checkState(BlockUtil.isOneBlock(h));
                consumer.consume(h, blockDir.newChild(c));
            }
        }

        InjectableFile tail = _f.newChild(TAIL);
        long tailLength = tail.lengthOrZeroIfNotFile();
        if (tailLength > 0) {
            byte[] d = partialDigest(tail, true).digest();
            consumer.consume(new ContentBlockHash(d), tail);
        }
    }

    private class ChunkingOutputStream extends OutputStream
    {
        private final MessageDigest md;

        // tail, i.e current chunk
        private long tailLength;
        private OutputStream out;
        private MessageDigest bmd;

        // HACK need to detect whether the digest has been generated in close()
        PrefixOutputStream pos;

        public ChunkingOutputStream(MessageDigest md) throws IOException {
            this.md = md;
            openTail();
        }

        private void commitTail(byte[] digest) throws IOException
        {
            final InjectableFile tail = _f.newChild(TAIL);
            InjectableFile block = _f.newChild(Util.join(BLOCKS, BaseUtil.hexEncode(digest)));
            block.getParentFile().ensureDirExists();
            if (!block.exists()) {
                tail.moveInSameFileSystem(block);
            } else {
                // TODO: size check?
                tail.deleteIgnoreError();
            }
            hashFile(tail).deleteIgnoreError();
            try (FileOutputStream bo = _f.newChild(BLOCK_HASH).newOutputStream(true)) {
                bo.write(digest);
                bo.getChannel().force(true);
            }
            // TODO: consistency checks on BLOCK_HASH file?
            checkState(!tail.exists());
        }

        private void commitTail() throws IOException
        {
            BlockStorage.l.debug("commit tail {} {}", _sokid, tailLength);
            out.close();
            out = null;
            commitTail(bmd.digest());
            bmd = null;
            tailLength = 0;
            openTail();
        }

        private void openTail() throws IOException
        {
            final InjectableFile tail = _f.newChild(TAIL);
            tailLength = tail.lengthOrZeroIfNotFile();
            if (tailLength > ClientParam.FILE_BLOCK_SIZE) {
                _f.deleteOrThrowIfExistRecursively();
                throw new IOException("corrupted prefix");
            }
            BlockStorage.l.debug("open tail {} {} {}", _sokid, tailLength,
                    hashFile(tail).lengthOrZeroIfNotFile());
            bmd = partialDigest(tail, tailLength > 0);
            out = new PrefixOutputStream(tail.newOutputStream(tailLength > 0), bmd) {
                @Override
                public void close() throws IOException
                {
                    try {
                        super.close();
                    } finally {
                        if (tailLength > 0) {
                            persistDigest(bmd, hashFile(tail));
                        }
                    }
                }
            };
        }

        public void write(int b) throws IOException {
            if (tailLength == ClientParam.FILE_BLOCK_SIZE) commitTail();
            out.write(b);
            ++tailLength;
        }

        public void write(@Nonnull byte b[]) throws IOException {
            write(b, 0, b.length);
        }

        public void write(@Nonnull byte b[], int off, int len) throws IOException {
            while (tailLength + len >= ClientParam.FILE_BLOCK_SIZE) {
                int n = (int)(ClientParam.FILE_BLOCK_SIZE - tailLength);
                out.write(b, off, n);
                off += n;
                len -= n;
                commitTail();
            }
            out.write(b, off, len);
            tailLength += len;
        }

        public void flush() throws IOException {
            out.flush();
        }

        @SuppressWarnings("try")
        public void close() throws IOException {
            // failure in openTail will leave this null
            if (out == null) return;
            try {
                flush();
                out.close();
            } finally {
                if (!pos.digested() && getLength_() > 0) {
                    persistDigest(md, _f.newChild(HASH));
                }
            }
        }
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        BlockPrefix to = (BlockPrefix)pf;
        to._f.deleteOrThrowIfExistRecursively();
        TransUtil.moveWithRollback_(_f, to._f, t);
    }

    private static final ContentBlockHash EMPTY_HASH = new ContentBlockHash(new byte[0]);

    ContentBlockHash hash() throws IOException
    {
        InjectableFile blocks = _f.newChild(BLOCK_HASH);
        byte[] d = blocks.exists() ? blocks.toByteArray() : null;

        InjectableFile tail = _f.newChild(TAIL);
        long tailLength = tail.lengthOrZeroIfNotFile();
        byte[] td = tailLength > 0 ? partialDigest(tail, true).digest() : null;

        // concat if needed
        if (d != null && td != null) {
            byte[] c = Arrays.copyOf(d, d.length + td.length);
            System.arraycopy(td, 0, c, d.length, td.length);
            return new ContentBlockHash(c);
        } else if (d == null && td == null) {
            return EMPTY_HASH;
        }

        return new ContentBlockHash(d != null ? d : td);
    }

    @Override
    public void delete_() throws IOException
    {
        _f.deleteOrThrowIfExistRecursively();
    }

    void cleanup_()
    {
        _f.deleteIgnoreErrorRecursively();

        InjectableFile p = _f.getParentFile();
        String[] children = _f.list();
        if (children == null || children.length == 0) p.deleteIgnoreError();
    }
}
