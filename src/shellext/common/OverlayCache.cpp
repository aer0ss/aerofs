#include "OverlayCache.h"

#if defined(_MSC_VER)
//typedef unsigned char uint8_t;
//typedef unsigned long uint32_t;
#define ROTL32(x,y)     _rotl(x,y)
#define FORCE_INLINE    __forceinline

// Some version of VS stdlib define min/max as macros, bad bad stupid VS...
#undef max

#include <stdint.h>
#include <stdlib.h>
#include <algorithm>
#else   // defined(_MSC_VER)

#include <stdint.h>
#include <stdlib.h>
#include <runetype.h>

#define FORCE_INLINE __attribute__((always_inline))

inline uint32_t rotl32(uint32_t x, int8_t r)
{
    return (x << r) | (x >> (32 - r));
}
#define ROTL32(x,y)     rotl32(x,y)

#endif // !defined(_MSC_VER)

#include <functional>

enum {
    MAX_AVERAGE_CHAIN_LENGTH = 1
};

/**
 * (key, value) pair with extra fields used by the LRU cache
 */
struct Node {
    std::wstring key;
    int value;

    // handle hash collisions through simple chaining
    Node* collision;

    /**
     * Double linking to implement LRU eviction on top of the hash table
     * The list is semi-circular, i.e head->prev == tail but tail->next == 0
     *
     * When a node is looked-up it is moved to the head of the queue, when the
     * cache is full it discards items from the tail of the queue.
     */
    Node* prev;
    Node* next;

    inline Node(const std::wstring& k, int v)
        : key(k), value(v), prev(0), next(0), collision(0) {}
};

namespace {

/**
 * NB: VS STL is widely known to suck, in particular the std::hash function is both
 * super slow and of such poor quality that it leads to a large number of collisions.
 * Besides, some platforms do not have a C++11-compliant compiler so we have to
 * provide a replacement anyway. MurmurHash is a well-tested and fast hash function
 * with pretty good collision properties that has been released into the public domain
 * by its author, Austin Appleby.
 * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp
 */
class MurmurHash32
{
    uint32_t m_seed;

    //-----------------------------------------------------------------------------
    // Block read - if your platform needs to do endian-swapping or can only
    // handle aligned reads, do the conversion here
    static FORCE_INLINE uint32_t getblock ( const uint32_t * p, int i )
    {
        return p[i];
    }

    //-----------------------------------------------------------------------------
    // Finalization mix - force all bits of a hash block to avalanche

    static FORCE_INLINE uint32_t fmix(uint32_t h)
    {
        h ^= h >> 16;
        h *= 0x85ebca6b;
        h ^= h >> 13;
        h *= 0xc2b2ae35;
        h ^= h >> 16;
        return h;
    }

    static uint32_t MurmurHash3_x86_32(const void* key, int len, uint32_t seed)
    {
        const uint8_t* data = (const uint8_t*)key;
        const int nblocks = len / 4;

        uint32_t h1 = seed;

        const uint32_t c1 = 0xcc9e2d51;
        const uint32_t c2 = 0x1b873593;

        const uint32_t * blocks = (const uint32_t *)(data + nblocks*4);
        for (int i = -nblocks; i; i++)
        {
            uint32_t k1 = getblock(blocks,i);

            k1 *= c1;
            k1 = ROTL32(k1,15);
            k1 *= c2;

            h1 ^= k1;
            h1 = ROTL32(h1,13);
            h1 = h1*5+0xe6546b64;
        }

        const uint8_t * tail = (const uint8_t*)(data + nblocks*4);

        uint32_t k1 = 0;
        switch(len & 3)
        {
            case 3: k1 ^= tail[2] << 16;
            case 2: k1 ^= tail[1] << 8;
            case 1: k1 ^= tail[0];
                k1 *= c1; k1 = ROTL32(k1,15); k1 *= c2; h1 ^= k1;
        };

        h1 ^= len;
        return fmix(h1);
    }

public:
    inline MurmurHash32() : m_seed(rand()) {}

    inline size_t operator () (const std::wstring& s) const
    {
        return MurmurHash3_x86_32(s.data(), int(s.length() * sizeof(wchar_t)), m_seed);
    }
};

static MurmurHash32 H;
static std::equal_to<std::wstring> E;

/**
 * Add a node to a hash table
 *
 * Collisions are handled by simple chaining: the most recent insertion
 * is added to the head of a singly linked list
 */
inline void add(Node** buckets, int numBuckets, Node* n)
{
    size_t i = H(n->key) % numBuckets;
    n->collision = buckets[i];
    buckets[i] = n;
}

/**
 * Convenience class that simplifies critical section management
 */
class CriticalSectionLocker
{
    mutex_t* m_cs;
public:
    CriticalSectionLocker(mutex_t* cs) : m_cs(cs)
    {
#if defined(_WIN32)
        EnterCriticalSection(m_cs);
#endif
    }

    ~CriticalSectionLocker()
    {
#if defined(_WIN32)
        LeaveCriticalSection(m_cs);
#endif
    }
};

}  // namespace

OverlayCache::OverlayCache(int limit)
    : m_limit(limit)
    , m_delegate(0)
    , m_entryCount(0)
    , m_bucketCount(0)
    , m_head(0)
    , m_buckets(0)
{
#if defined(_WIN32)
    InitializeCriticalSection(&m_cs);
#endif
}

OverlayCache::~OverlayCache()
{
    clear();
#if defined(_WIN32)
    DeleteCriticalSection(&m_cs);
#endif
}

int OverlayCache::count() const
{
    return m_entryCount;
}

int OverlayCache::limit() const
{
    return m_limit;
}

void OverlayCache::setLimit(int limit)
{
    if (limit <= 0) return;
    CriticalSectionLocker csLocker(&m_cs);
    m_limit = limit;
    trim();
}

void OverlayCache::clear()
{
    CriticalSectionLocker csLocker(&m_cs);
    free(m_buckets);
    m_bucketCount = 0;
    m_entryCount = 0;
    m_buckets = 0;

    // delete all nodes
    Node* h = m_head;
    m_head = 0;
    while (h) {
        Node* t = h->next;
        delete h;
        h = t;
    }
}

int OverlayCache::value(const std::wstring& key, int defaultValue)
{
    CriticalSectionLocker csLocker(&m_cs);
    Node* n = find(key);
    if (!n) return defaultValue;
    moveToHead(n);
    return n->value;
}

void OverlayCache::insert(const std::wstring& key, int value)
{
    CriticalSectionLocker csLocker(&m_cs);
    Node* n = find(key);
    if (!n) {
        if (++m_entryCount > MAX_AVERAGE_CHAIN_LENGTH*m_bucketCount) {
            resize(std::max(2*m_bucketCount, 10));
        }
        trim();
        moveToHead(new Node(key, value));
        add(m_buckets, m_bucketCount, m_head);
    } else {
        n->value = value;
        moveToHead(n);
    }
}

void OverlayCache::trim()
{
    while (0 < m_limit && m_limit < m_entryCount) {
        Node* t = m_head->prev;
        m_head->prev = t->prev;
        t->prev->next = 0;
        remove(t);
        --m_entryCount;
        if (m_delegate) m_delegate->evicted(t->key, t->value);
        delete t;
    }
}

OverlayCache::EvictionDelegate* OverlayCache::evictionDelegate() const
{
    return m_delegate;
}

void OverlayCache::setEvictionDelegate(EvictionDelegate* delegate)
{
    m_delegate = delegate;
}

void OverlayCache::remove(Node* n)
{
    size_t i = H(n->key) % m_bucketCount;
    Node* t = m_buckets[i];
    if (t == n) {
        m_buckets[i] = n->collision;
    } else {
        while (t && t->collision != n) {
            t = t->collision;
        }
        if (t) t->collision = n->collision;
    }
}

Node* OverlayCache::find(const std::wstring& key)
{
    if (m_bucketCount <= 0) return 0;
    size_t i = H(key) % m_bucketCount;
    Node* n = m_buckets[i];
    while (n && !E(n->key, key)) {
        n = n->collision;
    }
    return n;
}

/**
 * Move \a n to the head of a queue starting at \a head and return it
 */
void OverlayCache::moveToHead(Node* n)
{
    if (m_head == n) return;

    // keep track of old tail
    Node* tail = m_head ? m_head->prev : 0;

    // remove node from doubly-linked list
    if (n->prev) n->prev->next = n->next;
    if (n->next) n->next->prev = n->prev;

    // preserve semi-circularity: new_head->prev = tail
    if (tail != n) n->prev = tail ? tail : n;

    // insert node before old head
    if (m_head) m_head->prev = n;
    n->next = m_head;

    m_head = n;
}

void OverlayCache::resize(int numBuckets)
{
    // create new buckets
    Node** buckets = (Node**)calloc(numBuckets, sizeof(Node*));

    // rehash
    for (int i = 0; i < m_bucketCount; ++i) {
        Node* n = m_buckets[i];
        while (n) {
            Node* t = n->collision;
            add(buckets, numBuckets, n);
            n = t;
        }
    }

    // delete old buckets
    free(m_buckets);

    m_bucketCount = numBuckets;
    m_buckets = buckets;
}
