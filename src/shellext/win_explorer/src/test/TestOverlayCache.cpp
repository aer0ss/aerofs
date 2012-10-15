#include "StdAfx.h"

#include "Test.h"
#include "OverlayCache.h"

namespace {

TEST(OverlayCache, shouldCreateEmptyCache) {
	OverlayCache cache;

	COMPARE(0, cache.count());

	COMPARE(-1, cache.value(L""));
	COMPARE(0, cache.value(L"", 0));
	COMPARE(-1, cache.value(L"foo"));
	COMPARE(0, cache.value(L"foo", 0));
}

TEST(OverlayCache, shouldInsertItems) {
	OverlayCache cache;

	cache.insert(L"foo", 0);
	cache.insert(L"bar", -2);
	cache.insert(L"baz", 42);

	COMPARE(3, cache.count());

	COMPARE(-1, cache.value(L""));
	COMPARE(128, cache.value(L"", 128));
	COMPARE(0, cache.value(L"foo"));
	COMPARE(0, cache.value(L"foo", 128));
	COMPARE(-2, cache.value(L"bar"));
	COMPARE(-2, cache.value(L"bar", 128));
	COMPARE(42, cache.value(L"baz"));
	COMPARE(42, cache.value(L"baz", 128));
	COMPARE(-1, cache.value(L"foobar"));
	COMPARE(128, cache.value(L"foobar", 128));
}

TEST(OverlayCache, shouldReplaceItems) {
	OverlayCache cache;

	// have another item present to make sure replacement only affect the proper target
	cache.insert(L"bar", 42);
	for (int i = 0; i < 10; ++i) {
		cache.insert(L"foo", i);
		COMPARE(2, cache.count());
		COMPARE(i, cache.value(L"foo"));
		COMPARE(42, cache.value(L"bar"));
	}
}

TEST(OverlayCache, shouldEvictItems) {
	OverlayCache cache(2);

	cache.insert(L"foo", 1);
	cache.insert(L"bar", 2);

	COMPARE(2, cache.count());
	COMPARE(1, cache.value(L"foo"));
	COMPARE(2, cache.value(L"bar"));

	cache.insert(L"baz", 3);

	COMPARE(2, cache.count());
	COMPARE(-1, cache.value(L"foo"));
	COMPARE(2, cache.value(L"bar"));
	COMPARE(3, cache.value(L"baz"));
}

TEST(OverlayCache, shouldNotCrashWhenInterleavingInsertionLookupAndEviction) {
	OverlayCache cache(4);
	cache.insert(L"a", 1);
	cache.insert(L"b", 2);
	cache.insert(L"c", 3);
	COMPARE(2, cache.value(L"b"));
	cache.insert(L"d", 4);
	COMPARE(2, cache.value(L"b"));
	cache.insert(L"e", 5);
	cache.insert(L"f", 6);
	cache.insert(L"g", 7);
	COMPARE(-1, cache.value(L"d"));
}

TEST(OverlayCache, shouldMoveToHeadOnLookup) {
	OverlayCache cache(2);

	cache.insert(L"foo", 1);
	cache.insert(L"bar", 2);

	COMPARE(2, cache.count());
	COMPARE(2, cache.value(L"bar"));
	COMPARE(1, cache.value(L"foo"));

	cache.insert(L"baz", 3);

	COMPARE(2, cache.count());
	COMPARE(1, cache.value(L"foo"));
	COMPARE(-1, cache.value(L"bar"));
	COMPARE(3, cache.value(L"baz"));
}

TEST_P(OverlayCache, insertLookupBenchmark) {
	FETCH(int, limit);
	FETCH(int, minKeySize);
	FETCH(int, n);

	OverlayCache cache(limit);
	std::wstring base_key(minKeySize, rand());

	BENCHMARK("insertion(" << limit << " " << minKeySize << " " << n << "): ") {
		for (int i = 0; i < n; ++i) {
			cache.insert(WSTR(base_key << i), i);
		}
	}

	BENCHMARK("lookup(" << limit << " " << minKeySize << " " << n << "): ") {
		for (int i = 0; i < n; ++i) {
			bool ok = cache.value(WSTR(base_key << i)) == ((i >= n - limit) ? i : -1);
			VERIFY_MSG(ok, "insertion/eviction/lookup broken");
		}
	}
}

TEST_DATA(OverlayCache, insertLookupBenchmark) {
	addColumn<int>("limit");
	addColumn<int>("minKeySize");
	addColumn<int>("n");

	int limit = 10000;
	for (int mkl = 0; mkl <= 40; mkl += 20) {
		for (int n = 10; n <= 100000; n *= 10) {
			newRow(SSTR(limit << "," << mkl << "," << n)) << limit << mkl << n;
		}
	}
}

}  // namespace
