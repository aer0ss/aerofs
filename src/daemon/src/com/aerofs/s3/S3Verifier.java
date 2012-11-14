package com.aerofs.s3;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import com.aerofs.lib.SystemUtil;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor;
import com.aerofs.lib.aws.s3.db.S3Database;
import com.aerofs.lib.db.IDBIterator;

public class S3Verifier
{

    final static Logger l = Util.l(S3Verifier.class);

    private final S3Database _db;
    private final S3ChunkAccessor _s3ChunkAccessor;

    @Inject
    public S3Verifier(
            S3Database db,
            S3ChunkAccessor s3ChunkAccessor)
    {
        _db = db;
        _s3ChunkAccessor = s3ChunkAccessor;
    }

    public void launch_() throws SQLException, IOException
    {
        // make sure that the S3_ENC_PASS entered is correct before we encrypt
        // anything by attempting to decrypt an existing file.

        HashStream hs = HashStream.newFileHasher();
        byte[] buffer = new byte[4 << 10];

        IDBIterator<ContentHash> iter = _db.getAllChunks();
        try {
            while (iter.next_()) {
                ContentHash chunk = iter.get_();
                InputStream in = _s3ChunkAccessor.readOneChunk(chunk);
                try {
                    in = hs.wrap(in);
                    while (in.read(buffer) > 0) {
                    }
                } finally {
                    in.close();
                }
                ContentHash hash = hs.getHashAttrib();
                if (!hash.equals(chunk)) {
                    SystemUtil.fatal(">>>>>Hashed " + hash + " expected " + chunk);
                }
            }
        } finally {
            iter.close_();
        }
    }
}
