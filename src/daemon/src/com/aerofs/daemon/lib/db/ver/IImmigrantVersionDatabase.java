package com.aerofs.daemon.lib.db.ver;

import java.sql.SQLException;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.id.SOCID;

/**
 * Beyond the methods of IVersionDatabase, this interface represents persistent storage of immigrant
 * versions.
 */
public interface IImmigrantVersionDatabase extends IVersionDatabase<ImmigrantTickRow>
{

    void addImmigrantVersion_(SOCID socid, DID immDid, Tick immTick, DID did, Tick tick, Trans t)
            throws SQLException;

    /**
     * N.B. only VersionAssistant should call this method
     */
    void deleteImmigrantTicks_(SOCID socid, Trans t) throws SQLException;
}
