package com.aerofs.ssmp;

import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface ISslHandlerFactory {
    SslHandler newSslHandler() throws IOException, GeneralSecurityException;
}
