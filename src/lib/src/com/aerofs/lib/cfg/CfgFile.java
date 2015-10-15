package com.aerofs.lib.cfg;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public class CfgFile extends ICfgStore {

    private static final String DEFAULT_CONF_LOCATION = Util.join("/etc", LibParam.SA_CFG_FILE);

    @Inject
    public CfgFile(CfgAbsRTRoot cfgAbsRTRoot) throws IOException
    {
        readFromConfFile(new File(DEFAULT_CONF_LOCATION).exists() ? DEFAULT_CONF_LOCATION :
                Util.join(cfgAbsRTRoot.get(), LibParam.SA_CFG_FILE));
    }

    public void readFromConfFile(String confFile) throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(confFile));
        String line;
        Set<CfgKey> keysInCfgFile = Sets.newHashSet();
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split(" ", 2);
            Optional<CfgKey> cfgKey = CfgKey.getAllConfigKeys().stream().
                    filter(key -> key.keyString().equals(tokens[0])).findFirst();
            try {
                _map.put(cfgKey.get(), tokens[1]);
                keysInCfgFile.add(cfgKey.get());
            } catch (NoSuchElementException e) {
                // Ignore values not in default map.
            }

        }

        for (CfgKey keysNotInFile: Sets.difference(CfgKey.getAllConfigKeys(), keysInCfgFile)) {
            _map.put(keysNotInFile, keysNotInFile.defaultValue());
        }
    }
}
