package main

import (
	"fmt"
)

func lt(a, b uint64, c UID) bool {
	return a < c[0] || (a == c[0] && b < c[1])
}

// DID->version map, sorted by DID
// implemented as a sorted list
//  - more compact
//  - more cache-friendly
//  - number of entries expected to be small
type SortedVersionMap struct {
	d []uint64
}

func NewSortedVersionMap() *SortedVersionMap {
	return &SortedVersionMap{d: make([]uint64, 0, 3*16)}
}

func (m *SortedVersionMap) Size() int {
	return len(m.d) / 3
}

// decode and append a (DID, version) pair
// caller must ensure values are passed in sorted order
func (m *SortedVersionMap) Load(did UID, version uint64) error {
	d := m.d
	n := len(d)
	if n > 2 && !lt(d[n-3], d[n-2], did) {
		return fmt.Errorf("out-of-order load")
	}
	m.d = append(d, did[0], did[1], version)
	return nil
}

func (m *SortedVersionMap) PutOrRemove(did UID, version uint64) bool {
	if version == 0 {
		return m.Remove(did)
	}
	return m.Put(did, version)
}

func (m *SortedVersionMap) Put(did UID, version uint64) bool {
	d := m.d
	i := LowerBound(d, did)
	n := len(d)
	if i+2 < n && did[0] == d[i] && did[1] == d[i+1] {
		d[i+2] = version
		return false
	}
	if i == n {
		m.d = append(d, did[0], did[1], version)
		return true
	}
	d = append(d, d[n-3], d[n-2], d[n-1])
	copy(d[i+3:n], d[i:n-3])
	d[i+0] = did[0]
	d[i+1] = did[1]
	d[i+2] = version
	m.d = d
	return true
}

func (m *SortedVersionMap) Remove(did UID) bool {
	d := m.d
	i := LowerBound(d, did)
	n := len(d)
	if !(i+2 < n && did[0] == d[i] && did[1] == d[i+1]) {
		return false
	}
	copy(d[i:n-3], d[i+3:n])
	m.d = d[:n-3]
	return true
}

// Find insertion location
// i.e. either entry matching the given DID or first entry strictly superior to it
func LowerBound(d []uint64, did UID) int {
	lo := 0
	hi := len(d)
	for lo < hi {
		mid := 3 * ((lo + hi) / 6)
		if lt(d[mid], d[mid+1], did) {
			lo = mid + 3
		} else {
			hi = mid
		}
	}
	return lo
}

func (m *SortedVersionMap) ForAll(fn func(did UID, version uint64) bool) bool {
	d := m.d
	n := len(m.d)
	for i := 0; i+2 < n; i += 3 {
		if fn(UID([2]uint64{d[i], d[i+1]}), d[i+2]) {
			return true
		}
	}
	return false
}
