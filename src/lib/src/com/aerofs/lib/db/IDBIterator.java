package com.aerofs.lib.db;

import java.sql.SQLException;

/**
 * Iterator for items retrieved from SQL databases
 *
 * Usage:
 *
 *  IDBIter<Item> iter = ...;
 *  try {
 *      while (iter.next_()) {
 *          Item item = iter.get_();
 *          ...
 *      }
 *  } finally {
 *      iter.close_();
 *  }
 *
 * It is an error not to call close_() after use.
 *
 */
public interface IDBIterator<E> extends AutoCloseable
{
    /**
     * @return the current item. The result is undefined if next_() has never
     * been called, or next_() has returned false, or close_() has been called
     */
    E get_() throws SQLException;

    /**
     * Move to the next item
     *
     * @return false if there is no more items to retrieve
     * @throws SQLException
     */
    boolean next_() throws SQLException;

    /**
     * Release the resource associated with the iterator. May be called multiple
     * times without side effects
     */
    void close_() throws SQLException;

    /**
     * @return whether close_() has been called
     */
    boolean closed_();

    // for auto-close
    @Override
    default void close() throws SQLException { close_(); }
}
