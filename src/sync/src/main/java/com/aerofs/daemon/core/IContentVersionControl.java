package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

public interface IContentVersionControl {
    enum Granularity {
        File,
        Store
    }
    void fileExpelled_(SOID soid, Granularity g, Trans t) throws SQLException;
}
