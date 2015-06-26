package com.aerofs.ssmp;

import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface SslHandlerFactory {
    SslHandler newSslHandler() throws IOException, GeneralSecurityException;
}
