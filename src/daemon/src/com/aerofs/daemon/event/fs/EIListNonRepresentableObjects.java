/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject;
import com.google.common.collect.ImmutableCollection;

public class EIListNonRepresentableObjects extends AbstractEBIMC
{
    private ImmutableCollection<PBNonRepresentableObject> _nros;

    public EIListNonRepresentableObjects(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult(ImmutableCollection<PBNonRepresentableObject> nros)
    {
        _nros = nros;
    }

    public ImmutableCollection<PBNonRepresentableObject> nonRepresentableObjects()
    {
        return _nros;
    }
}
