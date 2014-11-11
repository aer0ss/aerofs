package com.aerofs.polaris.logical;

public interface Transactional<ReturnType> {

    ReturnType execute(DAO dao) throws Exception;
}
