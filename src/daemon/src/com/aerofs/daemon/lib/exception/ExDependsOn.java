package com.aerofs.daemon.lib.exception;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.protocol.dependence.DependencyEdge.DependencyType;
import com.aerofs.lib.id.OCID;

import javax.annotation.Nullable;

/**
 * used to abort the current download transaction to download another object on
 * which the object depends. the exception should be used only when a transaction
 * is ongoing and has to aborted. otherwise, downloading the requested object
 * from the current calling context is recommended, as it incurs less overhead
 * in exception handling and re-processing.
 *
 * N.B. This class could have consolidated with the Dependency Edge classes, but the Ex*DependsOn
 * exceptions don't know the source SOCID on purpose, so that throwers need not know the source.
 */
public class ExDependsOn extends Exception
{
    private static final long serialVersionUID = 1L;

    public final OCID _ocid;
    public final DID _did;
    public final DependencyType _type;
    public final boolean _ignoreError;

    public ExDependsOn(OCID ocid, @Nullable DID did, DependencyType type)
    {
        this(ocid, did, type, false);
    }

    protected ExDependsOn(OCID ocid, @Nullable DID did, DependencyType type, boolean ignoreError)
    {
        _ocid = ocid;
        _did = did;
        _type = type;
        _ignoreError = ignoreError;
    }
}
