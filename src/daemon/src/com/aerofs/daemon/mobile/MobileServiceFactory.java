/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.base.C;
import com.aerofs.base.net.CoreProtocolHandlers.RecvCoreProtocolVersionHandler;
import com.aerofs.base.net.CoreProtocolHandlers.SendCoreProtocolVersionHandler;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.ssl.SslHandler;

import static com.aerofs.base.BaseParam.MobileService.MAGIC_BYTES;

public class MobileServiceFactory implements ChannelPipelineFactory
{
    private static final int MAX_FRAME_LENGTH = 1 * C.MB;
    private static final int LENGTH_FIELD_SIZE = 4;

    private final IIMCExecutor _imce;
    private final SSLEngineFactory _sslEngineFactory;
    private final CfgLocalUser _cfgLocalUser;
    private final CfgLocalDID _cfgLocalDID;

    @Inject
    public MobileServiceFactory(CoreIMCExecutor cimce,
            CfgCACertificateProvider cfgCACertificateProvider,
            CfgKeyManagersProvider cfgKeyManagersProvider, CfgLocalUser cfgLocalUser,
            CfgLocalDID cfgLocalDID)
    {
        _imce = cimce.imce();
        _cfgLocalUser = cfgLocalUser;
        _cfgLocalDID = cfgLocalDID;

        // TODO (GS): Provide a CRL
        _sslEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop,
                cfgKeyManagersProvider, cfgCACertificateProvider, null);
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline pipeline = Channels.pipeline();
        appendToPipeline(pipeline);
        return pipeline;
    }

    public void appendToPipeline(ChannelPipeline p) throws Exception
    {
        MobileService mobileService = new MobileService(_imce);
        CNameVerificationHandler cnameHandler = new CNameVerificationHandler(_cfgLocalUser.get(),
                _cfgLocalDID.get());
        cnameHandler.setListener(mobileService);

        SslHandler sslHandler = new SslHandler(_sslEngineFactory.getSSLEngine());
        sslHandler.setCloseOnSSLException(true);
        p.addLast("ssl", sslHandler);
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0,
                LENGTH_FIELD_SIZE, 0, LENGTH_FIELD_SIZE));
        p.addLast("frameEncoder", new LengthFieldPrepender(LENGTH_FIELD_SIZE));
        p.addLast("magicHeaderWriter", new SendCoreProtocolVersionHandler(MAGIC_BYTES));
        p.addLast("magicHeaderReader", new RecvCoreProtocolVersionHandler(MAGIC_BYTES));
        p.addLast("cname", cnameHandler);
        p.addLast("mobileServiceHandler", new MobileServiceHandler(mobileService));
    }
}
