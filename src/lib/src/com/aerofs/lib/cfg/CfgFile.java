package com.aerofs.lib.cfg;

import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public class CfgFile extends ICfgStore {

    public CfgFile() throws IOException
    {
        readFromConfFile();
    }

    public void readFromConfFile() throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader("/etc/storage_agent.conf"));
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
