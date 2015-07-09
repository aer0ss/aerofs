package com.aerofs.daemon;

import com.aerofs.daemon.lib.IStartable;

/**
 * To be implemented by any module that:
 * <ol>
 *      <li>Has data structures that need to be explicitly initialized</li>
 *      <li>Has threads or tasks that need to be explicitly started</li>
 * </ol>
 */
public interface IModule extends IStartable
{
    /**
     * Perform data-structure setup operations for this module. Implementation of this method may
     * assume that all the objects it depends on have been constructed.
     *
     * @throws Exception if there is any setup error; if an exception is thrown this
     * module cannot be started and cannot be used
     */
    void init_() throws Exception;
}
