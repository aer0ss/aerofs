package com.aerofs.sp.authentication;

import com.google.common.io.ByteStreams;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkState;

public class DeploymentSecret {

    public static String getSecret() {
        try (InputStream is = new FileInputStream("/data/deployment_secret")) {
            String s = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8).trim();
            checkState(s.length() == 32, "Invalid deployment secret %s", s);
            return s;
        } catch (IOException e) {
            // The service can't start up correctly if the deployment secret isn't available,
            // so we throw an unchecked exception
            throw new IllegalStateException("Failed to load deployment secret", e);
        }
    }
}
