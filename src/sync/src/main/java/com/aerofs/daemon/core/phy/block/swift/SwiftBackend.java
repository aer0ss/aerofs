package com.aerofs.daemon.core.phy.block.swift;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.block.encrypted.EncryptedBackend;
import com.aerofs.lib.ContentBlockHash;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.*;

import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.StoredObject;

import java.io.InputStream;

/**
 * BlockStorage backend based on Openstack Swift
 */
public class SwiftBackend extends EncryptedBackend
{
    private static final Logger l = Loggers.getLogger(SwiftBackend.class);

    private SwiftConfig _swiftConfig;
    private Container currentContainer;
    private Account currentAccount;

    @Inject
    public SwiftBackend(SwiftConfig swiftConfig, SwiftConfig.CryptoConfig cryptoConfig)
    {
        super(cryptoConfig);
        _swiftConfig = swiftConfig;
        currentAccount = _swiftConfig.getAccount();
        currentContainer = getContainer(_swiftConfig.getContainerName());
    }

    /**
     * Grab the container or create it if it doesn't exist
     *
     * @param containerName The name of the container
     * @return Container container
     */
    public Container getContainer(String containerName)
    {
        l.debug("Getting container '{}'", containerName);

        currentContainer = currentAccount.getContainer(containerName);

        if (!currentContainer.exists()) {
            currentContainer.create();
            currentContainer.makePrivate();
        }

        return currentContainer;
    }

    /**
     * Read a block from the storage backend
     *
     * @param hash Hash of the object
     * @return object
     */
    @Override
    public InputStream getBlock(final ContentBlockHash hash) throws IOException
    {
        StoredObject object = acquireBlock(hash);
        l.debug("Got block \"{}\" at {}", getBlockKey(hash), object.getPublicURL());
        return wrapForDecoding(object.downloadObjectAsInputStream());
    }

    /**
     * Write a block to the storage backend
     *
     * @param hash Hash of the object
     * @param input Object
     * @param decodedLength (not used)
     */
    @Override
    public void putBlock(final ContentBlockHash hash, final InputStream input, final long decodedLength) throws IOException
    {
        StoredObject object = acquireBlock(hash);

        InputStream encryptedBlock = new BaseSecUtil.CipherFactory(_secretKey).encryptingHmacedInputStream(input);

        object.uploadObject(encryptedBlock);
        l.debug("Stored block \"{}\" at {}", getBlockKey(hash), object.getPublicURL());
    }

    /**
     * Delete a block from the storage backend
     *
     * @param hash Hash of the object
     * @param tk Unused
     */
    @Override
    public void deleteBlock(ContentBlockHash hash, TokenWrapper tk)
    {
        StoredObject object = acquireBlock(hash);
        object.delete();
    }

    private StoredObject acquireBlock(final ContentBlockHash hash)
    {
        String key = getBlockKey(hash);
        return currentContainer.getObject(key);
    }

    /**
     * For consistency with S3 backend, we store the chunks in a pseudo "subdirectory"
     * named "chunks" (BLOCK_DIR), and add a suffix ".chunk.gz.aes" (BLOCK_SUFFIX)
     *
     * @param hash The hash of the block
     * @return The key of the block
     */
    private String getBlockKey(final ContentBlockHash hash)
    {
        return BLOCK_DIR + hash.toHex() + BLOCK_SUFFIX;
    }
}
