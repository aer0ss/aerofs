/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.ids.SID;

import java.util.NoSuchElementException;

/**
 * Shared Folder Update Operation. Is used for async update operations
 * triggered by file system anchor events. These operations get saved in persistent queue
 * and processed asynchronously in the same order events happened
 *
 */
public interface ISharedFolderOp
{
    SID getSID();

    SharedFolderOpType getType();

    /**
     * Type of Shared Folder Operation. Type value is persisted in DB
     */
    static enum SharedFolderOpType
    {
        LEAVE(1), // leave shared folder
        RENAME(2); // rename shared folder

        private final int value;

        private SharedFolderOpType(int i)
        {
            value = i;
        }

        public int getValue()
        {
            return value;
        }

        static SharedFolderOpType fromValue(int value)
        {
            for (SharedFolderOpType item: SharedFolderOpType.values()) {
                if (item.value == value) {
                    return item;
                }
            }
            throw new NoSuchElementException("No Type for value: " + value);
        }
    }
}
