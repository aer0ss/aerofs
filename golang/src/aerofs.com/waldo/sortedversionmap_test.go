package main

import (
	"github.com/stretchr/testify/require"
	"testing"
)

type entry struct {
	d UID
	v uint64
}

func hv(h string, v uint64) entry {
	d, err := NewUIDFromHex(h)
	if err != nil {
		panic(err)
	}
	return dv(d, v)
}

func dv(d UID, v uint64) entry {
	return entry{d: d, v: v}
}

func hasEntries(t *testing.T, entries ...entry) func(UID, uint64) bool {
	i := 0
	return func(did UID, version uint64) bool {
		require.Equal(t, entries[i].d, did)
		require.Equal(t, entries[i].v, version)
		i++
		return false
	}
}

func TestSortedVersionMap_shouldLoad(t *testing.T) {
	d := NewSortedVersionMap()
	entries := []entry{
		hv("000102030405060708090a0b0c0d0e0f", 2),
		hv("000102030405060708090a0b0c0d0e10", 1),
		hv("010102030405060708090a0b0c0d0e0f", 3),
	}
	for _, e := range entries {
		require.Nil(t, d.Load(e.d, e.v))
	}
	require.Equal(t, len(entries), d.Size())
	require.False(t, d.ForAll(hasEntries(t, entries...)))
}

func TestSortedVersionMap_shouldPut(t *testing.T) {
	d := NewSortedVersionMap()
	m := make(map[UID]uint64)
	for i := 0; i < 1000; i++ {
		did := newUID()
		for {
			if _, present := m[did]; !present {
				break
			}
			did = newUID()
		}
		m[did] = uint64(i + 1)
		require.True(t, d.Put(did, uint64(i+1)))
	}

	require.Equal(t, len(m), d.Size())
	var prev UID
	require.False(t, d.ForAll(func(did UID, version uint64) bool {
		require.True(t, lt(prev[0], prev[1], did))
		require.Equal(t, m[did], version)
		prev = did
		return false
	}))
}

func TestSortedVersionMap_shouldRemove(t *testing.T) {
	d := NewSortedVersionMap()
	m := make(map[UID]uint64)
	for i := 0; i < 1000; i++ {
		did := newUID()
		for {
			if _, present := m[did]; !present {
				break
			}
			did = newUID()
		}
		m[did] = uint64(i + 1)
		require.True(t, d.Put(did, uint64(i+1)))
	}

	for did, v := range m {
		if (v % 2) == 0 {
			delete(m, did)
			require.True(t, d.Remove(did))
		}
	}

	require.Equal(t, len(m), d.Size())
	var prev UID
	require.False(t, d.ForAll(func(did UID, version uint64) bool {
		require.True(t, lt(prev[0], prev[1], did))
		require.Equal(t, m[did], version)
		prev = did
		return false
	}))
}

func BenchmarkSortedVersionMap_putEnd_10(b *testing.B) {
	bench_putEnd_n(b, 10)
}

func BenchmarkSortedVersionMap_putStart_10(b *testing.B) {
	bench_putStart_n(b, 10)
}

func BenchmarkSortedVersionMap_putEnd_100(b *testing.B) {
	bench_putEnd_n(b, 100)
}

func BenchmarkSortedVersionMap_putStart_100(b *testing.B) {
	bench_putStart_n(b, 100)
}

func BenchmarkSortedVersionMap_putEnd_1000(b *testing.B) {
	bench_putEnd_n(b, 1000)
}

func BenchmarkSortedVersionMap_putStart_1000(b *testing.B) {
	bench_putStart_n(b, 1000)
}

func BenchmarkSortedVersionMap_putEnd_10000(b *testing.B) {
	bench_putEnd_n(b, 10000)
}

func BenchmarkSortedVersionMap_putStart_10000(b *testing.B) {
	bench_putStart_n(b, 10000)
}

func bench_putEnd_n(b *testing.B, n int) {
	d := NewSortedVersionMap()
	did := UID([2]uint64{0, 0})
	for i := 0; i < b.N; i++ {
		d.Put(did, uint64(i+1))
		did[1]++
		if d.Size() > n {
			d = NewSortedVersionMap()
		}
	}
}

func bench_putStart_n(b *testing.B, n int) {
	d := NewSortedVersionMap()
	did := UID([2]uint64{^uint64(0), ^uint64(0)})
	for i := 0; i < b.N; i++ {
		d.Put(did, uint64(i+1))
		did[1]--
		if d.Size() > n {
			d = NewSortedVersionMap()
		}
	}
}

func BenchmarkSortedVersionMap_ForAll_10(b *testing.B) {
	bench_forall_n(b, 10)
}

func BenchmarkSortedVersionMap_ForAll_100(b *testing.B) {
	bench_forall_n(b, 100)
}

func BenchmarkSortedVersionMap_ForAll_1000(b *testing.B) {
	bench_forall_n(b, 1000)
}

func BenchmarkSortedVersionMap_ForAll_10000(b *testing.B) {
	bench_forall_n(b, 10000)
}

func bench_forall_n(b *testing.B, n int) {
	d := NewSortedVersionMap()
	did := UID([2]uint64{0, 0})
	for i := 0; i < n; i++ {
		d.Put(did, uint64(i+1))
		did[1]++
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		d.ForAll(func(_ UID, _ uint64) bool { return false })
	}
	b.StopTimer()
}
