package com.aerofs.daemon.core.phy;

import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;

import java.io.IOException;

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

    PrefixOutputStream newOutputStream_(boolean append) throws IOException;

    void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException;

    /**
     * This method should be called after a prefix is fully downloaded but before it is applied.
     */
    void prepare_(Token tk) throws IOException;

    void delete_() throws IOException;
}
