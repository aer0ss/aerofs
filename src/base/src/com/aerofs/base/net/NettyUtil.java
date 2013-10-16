package com.aerofs.base.net;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.base.ssl.SSLEngineFactory;
import org.jboss.netty.handler.ssl.NotSslRecordException;
import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class NettyUtil
{
    public static CNameVerificationHandler newCNameVerificationHandler(CNameListener listener,
            UserID localuser, DID localdid)
    {
        CNameVerificationHandler cnameHandler = new CNameVerificationHandler(localuser, localdid);
        cnameHandler.setListener(listener);
        return cnameHandler;
    }

    public static SslHandler newSslHandler(SSLEngineFactory sslEngineFactory)
            throws IOException, GeneralSecurityException
    {
        SslHandler sslHandler = new SslHandler(sslEngineFactory.getSSLEngine());
        sslHandler.setCloseOnSSLException(true);
        sslHandler.setEnableRenegotiation(false);
        return sslHandler;
    }

    public static Throwable truncateMessageIfNecessary(Throwable e)
    {
        if (e instanceof NotSslRecordException) {
            // NotSslRecordException.getMessage() includes the entire message, which can be huge.
            // For example, "not an SSL/TLS record: df77a927b10cd..." can be kilobytes long.
            // Therefore, we truncate it.
            return new NotSslRecordException(e.getMessage().substring(0, 30) + "... (truncated)");
        } else {
            return e;
        }
    }

}
