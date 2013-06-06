package com.aerofs.lib.cfg;

import java.io.File;

import com.aerofs.lib.LibParam;
import com.google.inject.Inject;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.db.IDatabaseParams;

/**
 * Parameters for the core database
 */
public class CfgCoreDatabaseParams implements IDatabaseParams
{
    private final CfgDatabase _cfgDatabase;
    private boolean _checkedMySQL;
    private boolean _isMySQL;

    @Inject
    public CfgCoreDatabaseParams(CfgDatabase cfgDatabase)
    {
        _cfgDatabase = cfgDatabase;
    }

    @Override
    public boolean isMySQL()
    {
        if (!_checkedMySQL) {
            _isMySQL = _cfgDatabase.getNullable(Key.MYSQL_URL) != null;
            _checkedMySQL = true;
        }
        return _isMySQL;
    }

    @Override
    public String url()
    {
        if (isMySQL()) {
//            return "jdbc:mysql://" + Cfg.db().getNullable(Key.MYSQL_URL) + "/" +
//                    (_cfgBuildType.isStaging() ? "aerofs_staging" : "aerofs_prod")
//                    + "?user=" + Cfg.db().getNullable(Key.MYSQL_LOGIN)
//                    + "&password=" + Cfg.db().getNullable(Key.MYSQL_PASSWD)
//                    + "&autoReconnect=true&useUnicode=true&characterEncoding=utf8";
            String url = Cfg.db().getNullable(Key.MYSQL_URL);
            StringBuilder sb = new StringBuilder(url);
            char sep = (url.indexOf('?') == -1) ? '?' : '&';
            String user = Cfg.db().getNullable(Key.MYSQL_LOGIN);
            if (user != null) {
                sb.append(sep).append("user=").append(Util.urlEncode(user));
                sep = '&';
            }
            String password = Cfg.db().getNullable(Key.MYSQL_PASSWD);
            if (password != null) {
                sb.append(sep).append("password=").append(Util.urlEncode(password));
                sep = '&';
            }
            sb.append(sep).append("autoReconnect=true&useUnicode=true&characterEncoding=utf8");
//            sb.append("&profileSQL=true");
            return sb.toString();
        } else {
            return "jdbc:sqlite:" + Cfg.absRTRoot() + File.separator + LibParam.CORE_DATABASE;
        }
    }

    @Override
    public boolean sqliteExclusiveLocking()
    {
        return true;
    }

    @Override
    public boolean sqliteWALMode()
    {
        return true;
    }

    @Override
    public boolean autoCommit()
    {
        return false;
    }
}
