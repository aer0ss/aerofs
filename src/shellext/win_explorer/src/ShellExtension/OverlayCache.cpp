#include "StdAfx.h"
#include "OverlayCache.h"

#include <assert.h>
#include <functional>

#include "logger.h"

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
 * In the interest of simplicity and maintainability we'll keep using it until the
 * performance of the shellext becomes a concern.
 */
static std::hash<std::wstring> H;
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
	CRITICAL_SECTION* m_cs;
public:
	CriticalSectionLocker(CRITICAL_SECTION* cs) : m_cs(cs)
	{
		EnterCriticalSection(m_cs);
	}

	~CriticalSectionLocker()
	{
		LeaveCriticalSection(m_cs);
	}
};

}  // namespace

OverlayCache::OverlayCache(int limit)
	: m_limit(limit)
	, m_entryCount(0)
	, m_bucketCount(0)
	, m_head(0)
	, m_buckets(0)
{
	InitializeCriticalSection(&m_cs);
}

OverlayCache::~OverlayCache()
{
	clear();
	DeleteCriticalSection(&m_cs);
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
			resize(max(2*m_bucketCount, 10));
		}
		trim();
		moveToHead(new Node(key, value));
		add(m_buckets, m_bucketCount, m_head);
	} else {
		n->value = value;
		moveToHead(n);
	}
}

void OverlayCache::trim() {
	while (0 < m_limit && m_limit < m_entryCount) {
		Node* t = m_head->prev;
		m_head->prev = t->prev;
		t->prev->next = 0;
		remove(t);
		--m_entryCount;
		delete t;
	}
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
