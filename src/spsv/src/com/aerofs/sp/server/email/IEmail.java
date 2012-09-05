package com.aerofs.sp.server.email;

import java.io.IOException;

public interface IEmail {

    public static enum HEADER_SIZE {
        H1,
        H2
    }

    public abstract void addSection(final String header,
                    final HEADER_SIZE size, final String body)
            throws IOException;

    /**
     *
     * @param valediction "Yours Sincerely", "Yours", "Best Regards", etc...
     * @param name
     * @param ps
     */
    public abstract void addSignature(final String valediction,
                    final String name, final String ps)
            throws IOException;

}
