/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkState;

/**
 * Simple helper class that reads a IPhysicalFile into chunks, while taking into account some
 * platform specificities.
 */
public class FileChunker
{
    private final static Logger l = Loggers.getLogger(FileChunker.class);

    static final int QUEUE_SIZE_WINDOWS = 128; // See comment below

    private final IPhysicalFile _file;
    private final long _mtime;
    private final long _fileLength;

    private final int _chunkSize;
    private final long _endPos;
    private long _readPosition;
    private InputStream _is;
    private boolean _isWindows;

    // On Windows, we will close and reopen the file every time we read, to avoid holding
    // file locks (see comment below). But every time we open a file for reading, this may
    // trigger an anti-virus scan on the file, resulting in degraded performance. In the past,
    // we used to do this on each 8 KB chunk, and if we're targetting a speed of 30 MB/s, this
    // means ~3840 open/close per second.
    //
    // In order to work around this problem, we now read several chunks at once and put them
    // in this queue. The queue size is 128 chunks on Windows, which amounts to 1 MB of data
    // (128 * 8 KB) which in turn amounts to only 30 open and close cycles per second if we target
    // 30 MB/s. This should be sufficiently low to keep the antivirus happy. On non-Windows
    // platforms, we keep the queue size at 1 since this makes no performance difference and we
    // don't want to waste memory.
    private Queue<byte[]> _chunksQueue = Queues.newArrayDeque();
    private final int _queueSize;

    /**
     * @param fileLength Total size in bytes in the file
     * @param startPos   Byte offset here to start reading in the file. (ie: prefix)
     * @param chunkSize  How large the chunks should be.
     * @param isWindows  Whether we are on the Windows platform. We do not use OSUtil to make this
     *                   class testable.
     */
    public FileChunker(IPhysicalFile file, long mtime, long fileLength,
            long startPos, int chunkSize, boolean isWindows)
    {
        this(file, mtime, fileLength, startPos, fileLength, chunkSize, isWindows);
    }

    public FileChunker(IPhysicalFile file, long mtime, long fileLength,
            long startPos, long endPos, int chunkSize, boolean isWindows)
    {
        checkState(startPos <= endPos);
        checkState(endPos <= fileLength);

        _file = file;
        _mtime = mtime;
        _fileLength = fileLength;
        _readPosition = startPos;
        _endPos = endPos;
        _chunkSize = chunkSize;
        _queueSize = isWindows ? QUEUE_SIZE_WINDOWS : 1;
        _isWindows = isWindows;
    }

    public byte[] getNextChunk_()
            throws IOException, ExUpdateInProgress
    {
        if (_chunksQueue.isEmpty()) {
            readChunks_();
        }

        return _chunksQueue.poll();
    }

    public void close_()
            throws IOException
    {
        if (_is != null) _is.close();
    }

    private void readChunks_()
            throws IOException, ExUpdateInProgress
    {
        // Open the stream if it has been closed (or if this is the first time)
        if (_is == null) {
            _is = _file.newInputStream_();
            if (_is.skip(_readPosition) != _readPosition) {
                throw new ExUpdateInProgress("skip() fell short");
            }
        }

        // Fill the queue with chunks
        for (int i = 0; i < _queueSize; i++) {
            if (_readPosition == _endPos) break;
            checkState(_readPosition < _endPos);

            byte[] buf = new byte[(int)Math.min(_chunkSize, _endPos - _readPosition)];
            int bytesCopied = readChunk(buf, _is);
            if (bytesCopied != buf.length) {
                throw new ExUpdateInProgress("unexpected end of stream");
            }
            _chunksQueue.add(buf);
            _readPosition += buf.length;
        }

        // To avoid race conditions and potential corruptions we need to explicitly check
        // for changes to the physical file before loaded chunks can be used
        if (_file.wasModifiedSince(_mtime, _fileLength)) {
            String msg = "mtime,length changed: expected=("
                    + _mtime +  "," + _fileLength + ") actual=("
                    + _file.getLastModificationOrCurrentTime_() + "," + _file.getLength_() + ")";
            l.info(msg);
            throw new ExUpdateInProgress(msg);
        }

        // On Windows, a FileInputStream holds a shared read lock and a delete lock on the file,
        // which means that the file can't be deleted and can't be opened for exclusive read
        // access while the input stream is open. So we close the input stream after reading to
        // avoid holding the locks while waiting on network I/O.
        if (_isWindows) {
            _is.close();
            _is = null;
        }
    }

    private static int readChunk(byte[] buf, InputStream is) throws IOException
    {
        int total = 0;
        while (total < buf.length) {
            int read = is.read(buf, total, buf.length - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }
}
