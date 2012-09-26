#pragma once

#include <string>

struct Node;


/**
 * LRU cache for overlays: path -> int
 *
 * This class is thread-safe
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

private:
	void trim();

	Node* find(const std::wstring& key);
	void remove(Node *n);
	void moveToHead(Node *n);
	void resize(int numBuckets);

	CRITICAL_SECTION m_cs;

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
