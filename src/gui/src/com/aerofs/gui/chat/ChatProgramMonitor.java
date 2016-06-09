package com.aerofs.gui.chat;

import com.aerofs.auth.client.cert.AeroDeviceCert;
import com.aerofs.base.*;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.NioChannelFactories;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.swig.driver.DriverConstants;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestEncoder.ErrorDataEncoderException;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class ChatProgramMonitor {
    private final static Logger l = Loggers.getLogger(ChatProgramMonitor.class);

    private final IOSUtil _os = OSUtil.get();
    private final InjectableDriver _driver = new InjectableDriver(_os);

    private Thread _t;
    private Process _p;

    private static class MergedResponse {
        // common
        Long expiresIn;

        // get token
        String accessToken;
        String tokenType;
        String refreshToken;
        String scope;

        // verify token
        String audience;
        Set<String> scopes;
        AuthenticatedPrincipal principal;
        String error;
        String mdid;
    }

    private final String basicAuth = "Basic " + Base64.encodeBytes(BaseUtil.string2utf(
            getStringProperty("daemon.oauth.id", "oauth-havre") + ":" +
            getStringProperty("daemon.oauth.secret", "i-am-not-a-restful-secret")
    ));

    private final SimpleHttpClient<String, MergedResponse> _bifrost =
            new SimpleHttpClient<String, MergedResponse>(
                    uri(),
                    new CfgCACertificateProvider(),
                    new CfgKeyManagersProvider(),
                    NioChannelFactories.getClientChannelFactory(),
                    TimerUtil.getGlobalTimer()
            ) {
        @Override
        public String buildURI(String query) {
            return _endpoint.getPath() + (query == null
                    ? "delegate/token"
                    : "tokeninfo?access_token=" + query
            );
        }
        @Override
        public void modifyRequest(HttpRequest req, String query) {
            if (query == null) {
                // request new token
                req.setMethod(HttpMethod.POST);
                req.headers().set(Names.AUTHORIZATION, AeroDeviceCert.getHeaderValue(
                        Cfg.user().getString(), Cfg.did().toStringFormal()));
                req.headers().set(Names.CONTENT_TYPE, "application/x-www-form-urlencoded");
                try {
                    HttpPostRequestEncoder form = new HttpPostRequestEncoder(req, false);
                    form.addBodyAttribute("client_id", "aerofs-trifrost");
                    form.addBodyAttribute("grant_type", "delegated");
                    form.addBodyAttribute("scope", "files.read,files.write,files.appdata,acl.read,acl.write");
                    form.finalizeRequest();
                } catch (ErrorDataEncoderException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // verify token
                req.headers().set(Names.AUTHORIZATION, basicAuth);
            }
        }
    };

    public ChatProgramMonitor() {}

    public boolean start() throws Exception {
        if (_t != null) {
            if (_p.isAlive()) {
                _p.getOutputStream().write("focus\n".getBytes(StandardCharsets.UTF_8));
                _p.getOutputStream().flush();
                return false;
            }
            stop();
        }

        // though shalt not have multiple Amium instances running at the same time
        kill();

        String eyja;
        switch (_os.getOSFamily()) {
        case WINDOWS:
            eyja = "eyja.exe";
            break;
        case OSX:
            eyja = "Eyja.app/Contents/MacOS/Eyja";
            break;
        default:
            eyja = "eyja";
        }

        ProcessBuilder pb = new ProcessBuilder(Util.join(AppRoot.abs(), eyja));
        pb.redirectErrorStream(true);
        pb.environment().put("AERO_USER", Cfg.user().getString());
        pb.environment().put("AERO_TOKEN", token());
        pb.environment().put("AERO_DATA", Util.join(Cfg.absRTRoot(), "chat"));
        try {
            _p = pb.start();
        } catch (IOException e) {
            l.error("failed to start", e);
        }
        _t = new Thread(this::outputHandler);
        _t.start();
        return true;
    }

    private void kill() throws IOException
    {
        String eyja;
        switch (_os.getOSFamily()) {
        case WINDOWS:
            eyja = "amium-messaging.exe";
            break;
        case OSX:
            eyja = "Amium";
            break;
        default:
            eyja = "amium-messaging";
        }
        // If one of the processes failed to be killed, throw an exception
        if (_driver.killProcess(eyja) == DriverConstants.DRIVER_FAILURE) {
            throw new IOException("failed to kill daemon process");
        }
    }

    private static URI uri() {
        URI u = URI.create(ConfigurationProperties.getStringProperty("base.sp.url", null));
        return URI.create("https://" + u.getHost() + ":" + u.getPort() + "/auth/");
    }

    private String token() throws Exception {
        String token = Cfg.db().getNullable(CfgDatabase.OAUTH_TOKEN);
        if (token != null) {
            try {
                MergedResponse r = _bifrost.send(token).get(5, TimeUnit.SECONDS);
                if (r.error == null && Cfg.did().toStringFormal().equals(r.mdid)) {
                    l.debug("token valid");
                    return token;
                }
                l.info("token invalid: {} {}", r.error, r.mdid);
            } catch (Exception e) {
                l.info("token verification failed", e);
            }
        }

        MergedResponse r = _bifrost.send(null).get(5, TimeUnit.SECONDS);
        l.debug("new token");
        token = r.accessToken;

        Cfg.db().set(CfgDatabase.OAUTH_TOKEN, token);
        return token;
    }

    public void stop() {
        if (_t == null) return;
        if (_p.isAlive()) _p.destroy();
        try {
            _t.join();
        } catch (InterruptedException e) {
            l.error("interrupted", e);
        }
        _t = null;
    }

    private void outputHandler() {
        int idx = 0;
        byte[] out = new byte[1024];
        while (true) {
            try {
                int n = _p.getInputStream().read(out, idx, out.length - idx);
                if (n == -1) {
                    break;
                }
                int i, last = 0;
                for (i = idx; i < idx + n; ++i) {
                    if (out[i] == '\n') {
                        String line = new String(out, last, i - last, StandardCharsets.UTF_8);
                        last = i + 1;
                        l.info("out: {}", line);
                    }
                }
                idx = i;
                if (last != 0) {
                    System.arraycopy(out, last, out, 0, idx - last);
                    idx -= last;
                } else if (idx == out.length) {
                    l.warn("out. {}", new String(out, StandardCharsets.UTF_8));
                    idx = 0;
                }
            } catch (IOException e) {
                l.error("", e);
            }
        }
        l.info("done {} {}", _p.isAlive(), _p.isAlive() ? -1 : _p.exitValue());
    }
}
