package com.aerofs.downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

public class Main {

    static final String PRODUCT_NAME = "AeroFS";
    static final String TITLE = "Downloading " + PRODUCT_NAME + " for initial setup...";

    /**
     * parameters:  ["gui"|"cli"] [url] [dest_path]
     * exit code: 0 if download succeeds, non-0 otherwise
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3) throw new Exception("bad paramaters");

        boolean gui = args[0].equals("gui");
        final String url = args[1];
        final String dest = args[2];

        final IProgressIndicator pi = gui ? new GUIDownloader() :
            new CLIDownloader();

        pi.run(new Runnable() {
            @Override
            public void run()
            {
                try {
                    URLConnection c = newAWSConnection(new URL(url));
                    c.setReadTimeout(30 * 1000);
                    int len = c.getContentLength();
                    pi.setTotal(len);

                    new File(dest).delete();
                    FileOutputStream os = new FileOutputStream(dest);
                    try {
                        InputStream is = c.getInputStream();
                        try {
                            byte[] bs = new byte[4 * 1024];
                            int count = 0;
                            while (true) {
                                int read = is.read(bs);
                                if (read < 0) break;
                                os.write(bs, 0, read);
                                count += read;
                                pi.setProgress(count);
                            }
                            pi.complete();

                        } finally {
                            is.close();
                        }
                    } finally {
                        os.close();
                    }
                } catch (Throwable e) {
                    pi.error(e);
                    // the error() above must have terminated the program
                    assert false;
                }
            }
        });
    }

    /**
     * Because the download URLs redirect (as CNAMEs) to AWS servers, which have their own SSL
     * certificate, we need to work around for cert verification to pass.
     *
     * HOWEVER, an attacker can still hijack the DNS and redirect the URLs to their own AWS servers.
     * The ultimate solution is to sign installer / patcher binaries.
     *
     * N.B. This method must be identical to the same method in Updater
     */
    private static URLConnection newAWSConnection(URL url) throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {

                try {
                    X509Certificate[] x509 = session.getPeerCertificateChain();
                    for (int i = 0; i < x509.length; i++) {
                        String str = x509[i].getSubjectDN().toString();
                        // this is for URLs pointing to S3
                        if (str.startsWith("CN=*.s3.amazonaws.com")) return true;
                        // this is for URLs pointing to CloudFront
                        if (str.startsWith("CN=*.cloudfront.net")) return true;
                    }
                    System.err.println("expected CN not found");
                    return false;
                } catch (SSLPeerUnverifiedException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });

        return conn;
    }

    public static String getErrorMessage(Throwable e)
    {
        return "Download failed (" + e.getMessage() + "). " + "Please try again later.";
    }
}
