package com.aerofs.trifrost;

import com.aerofs.trifrost.api.VerifiedDevice;

import java.io.IOException;

/**
 * Created by jon on 11/12/15.
 */
public interface ISpartaClient {
    VerifiedDevice getTokenForUser(String principal) throws IOException;
}
