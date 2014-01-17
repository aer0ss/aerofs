package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;

/**
 * This class represents temporary files used for downloads.
 *
 * {@link IPhysicalStorage#apply_}
 * applies a completely downloaded file.
 */
public interface IPhysicalPrefix
{
    /**
     * @return 0 if the file doesn't exist
     */
    long getLength_();

    OutputStream newOutputStream_(boolean append) throws IOException;

    InputStream newInputStream_() throws IOException;

    void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException;

    ContentHash prepare_(Token tk) throws IOException;

    void delete_() throws IOException;

    void truncate_(long length) throws IOException;
}
