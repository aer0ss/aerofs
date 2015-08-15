package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;

public class CsvFilter {
    private static final Logger l = LoggerFactory.getLogger(CsvFilter.class);
    private final CfgAbsRTRoot cfgAbsRtRoot;
    private final ImmutableList<UserID> _users;

    @Inject
    public CsvFilter(CfgAbsRTRoot cfgAbsRTRoot) {
        this.cfgAbsRtRoot = cfgAbsRTRoot;
        this._users = loadCsv();
    }

    private ImmutableList<UserID> loadCsv() {
        List<UserID> users = Lists.newArrayList();
        try {
            Scanner s = new Scanner(new File(Util.join(cfgAbsRtRoot.get(), "users.csv")));
            s.useDelimiter(",");
            try {
                while (s.hasNext()) {
                    try {
                        UserID u = UserID.fromExternal(s.next());
                        users.add(u);
                        l.info("User {} added.", u);
                    } catch (ExInvalidID e) {}
                }
            } finally {
                s.close();
            }
        } catch (FileNotFoundException e) {
            l.info("No users.csv is found.");
        }
        return ImmutableList.copyOf(users);
    }

    public List<UserID> get(){

        return _users;
    }
}
