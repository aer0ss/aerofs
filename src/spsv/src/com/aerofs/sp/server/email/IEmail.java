package com.aerofs.sp.server.email;

import java.io.IOException;

public interface IEmail {

    public abstract void addSection(String header, String body) throws IOException;

    /**
     * @param valediction "Yours Sincerely", "Yours", "Best Regards", etc...
     *
     * TODO (WW) get rid of this method completely?
     */
    public abstract void addSignature(String valediction, String name, String ps)
            throws IOException;
}
