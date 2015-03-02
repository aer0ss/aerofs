package com.aerofs.daemon.event.lib.imc;

public interface IResultWaiter {

    void okay();

    void error(Exception e);
}
