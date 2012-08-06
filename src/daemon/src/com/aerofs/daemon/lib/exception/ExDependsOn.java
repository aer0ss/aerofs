package com.aerofs.daemon.lib.exception;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OCID;

/**
 * used to abort the current download transaction to download another object on
 * which the object depends. the exception should be used only when a transaction
 * is ongoing and has to aborted. otherwise, downloading the requested object
 * from the current calling context is recommended, as it incurs less overhead
 * in exception handling and re-processing.
 */
public class ExDependsOn extends Exception {

    private static final long serialVersionUID = 1L;

    private final OCID _ocid;
    private final DID _did;
    private final boolean _ignoreError;

    /**
     * @param did null if not specified
     */
    public ExDependsOn(OCID ocid, DID did, boolean ignoreError)
    {
        _ocid = ocid;
        _did = did;
        _ignoreError = ignoreError;
    }

    public OCID ocid()
    {
        return _ocid;
    }

    /**
     * @return null if not specified
     */
    public DID did()
    {
        return _did;
    }

    public boolean ignoreError()
    {
        return _ignoreError;
    }
}
