package com.aerofs.daemon.lib.exception;

import com.aerofs.daemon.core.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.lib.id.OCID;

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
    public final DependencyType _type;

    public ExDependsOn(OCID ocid, DependencyType type)
    {
        _ocid = ocid;
        _type = type;
    }
}
