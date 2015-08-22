package com.aerofs.base;

import com.aerofs.testlib.SecTestUtil;
import org.junit.Test;

import java.io.File;
import java.security.PrivateKey;
import java.security.SecureRandom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 */
public class TestBaseSecUtil {
    private final String filename = "himom.key";

    @Test
    public void shouldRemovePrivKeyOnFailure() throws Exception
    {
        PrivateKey privateKey = new PrivateKey() {
            static final long serialVersionUID = 0L;
            @Override
            public String getAlgorithm() {
                throw new UnsupportedOperationException("this is a poisoned key");
            }

            @Override
            public String getFormat() {
                throw new UnsupportedOperationException("this is a poisoned key");
            }

            @Override
            public byte[] getEncoded() {
                throw new UnsupportedOperationException("this is a poisoned key");
            }
        };
        try {
            BaseSecUtil.writePrivateKey(privateKey, filename);
            fail("shouldn't get here");
        } catch (UnsupportedOperationException expected) { }

        assertFalse(new File(filename).exists());
    }

    @Test
    public void shouldRemovePreExistingPrivKey() throws Exception {
        PrivateKey privateKey = SecTestUtil
                .generateKeyPairNoCheckedThrow(new SecureRandom())
                .getPrivate();
        BaseSecUtil.writePrivateKey(privateKey, filename);

        // second call shouldn't die, just replace the file...
        BaseSecUtil.writePrivateKey(privateKey, filename);

        new File(filename).delete();
    }
}