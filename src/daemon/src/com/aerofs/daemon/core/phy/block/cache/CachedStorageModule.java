/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.block.BlockStorageModule;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.google.inject.Provider;
import com.google.inject.Scopes;

/**
 * To enable caching in any backend simply make the backend Module inherit from this class instead
 * of BlockStorageModule
 */
public abstract class CachedStorageModule extends BlockStorageModule
{
    @Override
    public void configure()
    {
        super.configure();

        // override backend binding with a provider that wraps a cache around the actual backend
        bind(IBlockStorageBackend.class)
                .toProvider(new CacheProxyProvider(getProvider(IBlockStorageBackend.class)))
                .in(Scopes.SINGLETON);
    }

    /**
     * Too bad Guice doesn't support some kind of proxy-binding out of the box
     * (somebody proposed a patch to that effect but it was rejected by the maintainers)
     */
    class CacheProxyProvider implements Provider<IBlockStorageBackend>
    {
        Provider<IBlockStorageBackend> _targetProvider;

        CacheProxyProvider(Provider<IBlockStorageBackend> target)
        {
            _targetProvider = target;
        }

        @Override
        public IBlockStorageBackend get()
        {
            return new CacheBackend(
                    getProvider(CfgAbsAuxRoot.class).get(),
                    getProvider(TransManager.class).get(),
                    getProvider(CoreScheduler.class).get(),
                    getProvider(CacheDatabase.class).get(),
                    _targetProvider.get());
        }
    }
}
