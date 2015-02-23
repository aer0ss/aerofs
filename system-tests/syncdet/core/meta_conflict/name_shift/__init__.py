"""
This package contains tests for name conflicts caused by a swap, or more
generally a "shift", of the file names for a set of OIDs across two peers.
(We could test across >2 peers, but this suffices for now)

A name swap is observed between two objects with OIDs o1 and o2. On one peer
they have file names n1 and n2, respectively, on the other, n2 and n1,
respectively. The AeroFS algorithm must sort out the name conflict gracefully.

The term name shift is observed among three objects: o1, o2, and o3. On one
peer the objects have names n1, n2, n3. On the other peer the names
have been "shifted" to n2, n3, and n1, respectively.
"""
