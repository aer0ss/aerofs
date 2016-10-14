/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.test;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.google.protobuf.ByteString;

import java.util.List;

public class EITestGetAliasObject extends AbstractEBIMC
{
    public final Path _path;

    public List<ByteString> _oids;

    public EITestGetAliasObject(Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
    }

    public void setResult_(List<ByteString> r)
    {
        _oids = r;
    }
}
