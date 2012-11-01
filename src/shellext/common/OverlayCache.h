#ifndef OVERLAY_CACHE_H
#define OVERLAY_CACHE_H

#include <string>

struct Node;

/**
 * At this time we only support internal synchronization on Windows
 * This is currently not a problem as the Finder extension uses an Objective-C wrapper which does its own
 * synchronization.
 */
#if defined(_WIN32)
#include <windows.h>
typedef CRITICAL_SECTION mutex_t;
#else
typedef void* mutex_t;
#endif

/**
 * LRU cache for overlays: path -> int
 *
 * This class is thread-safe on Windows
 *
 * NB: This class is used by both the OSX and Windows shell extensions. Any change should be thoroughly tested on
 * both platforms.
 */
class OverlayCache
{
public:
    OverlayCache(int limit = 1000);
    ~OverlayCache();

    int count() const;

    int limit() const;
    void setLimit(int limit);

    void clear();

    int value(const std::wstring& key, int defaultValue = -1);
    void insert(const std::wstring& key, int value);

    class EvictionDelegate
    {
    public:
        virtual ~EvictionDelegate() {}
        virtual void evicted(const std::wstring& key, int value) const = 0;
    };

    EvictionDelegate* evictionDelegate() const;
    void setEvictionDelegate(EvictionDelegate* delegate);

private:
    void trim();

    Node* find(const std::wstring& key);
    void remove(Node *n);
    void moveToHead(Node *n);
    void resize(int numBuckets);

    mutex_t m_cs;
    EvictionDelegate* m_delegate;

    /**
     * Use a custom LRU implementation instead of piggybacking on STL containers as benchmarks
     * show a significant advantage both in terms of memory usage and clock cycles.
     */
    int m_limit;
    int m_entryCount;
    int m_bucketCount;
    Node* m_head;
    Node** m_buckets;
};

#endif  // OVERLAY_CACHE_H
