package com.aerofs.daemon.core.ds;

import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

public interface IPathResolver {
    ResolvedPath resolveNullable_(SOID soid) throws SQLException;
}
