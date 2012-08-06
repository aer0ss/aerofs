package com.aerofs.daemon.core.net;

public interface IDuplexLayer extends IUnicastInputLayer, IUnicastOutputLayer
{

    public void init_(IUnicastInputLayer upper, IUnicastOutputLayer lower);
}
