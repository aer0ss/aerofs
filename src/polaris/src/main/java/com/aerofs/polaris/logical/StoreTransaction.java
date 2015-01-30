package com.aerofs.polaris.logical;

public interface StoreTransaction<ReturnType> {

    ReturnType execute(DAO dao) throws Exception;
}
