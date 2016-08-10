package main

import (
	"fmt"
	"github.com/stretchr/testify/require"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"
)

type testCtx struct {
	dir string
	db  *DB
}

func NewContext() testCtx {
	d, err := ioutil.TempDir("", "test")
	if err != nil {
		panic(err)
	}
	db, err := OpenDB(filepath.Join(d, "db"))
	if err != nil {
		panic(err)
	}
	return testCtx{dir: d, db: db}
}

func (c testCtx) Cleanup() {
	os.RemoveAll(c.dir)
}

func (c testCtx) checkVersions(t testing.TB, oid UID, expected ...entry) {
	var actual []entry
	_, err := c.db.ForAllDevices(oid, func(did UID, version uint64) bool {
		actual = append(actual, dv(did, version))
		return false
	})
	require.Nil(t, err)
	require.Equal(t, expected, actual)
}

func TestDB_shouldSetAvailableBatch_1(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 1)
}

func TestDB_shouldSetAvailableBatch_10(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 10)
}

func TestDB_shouldSetAvailableBatch_50(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 50)
}

func TestDB_shouldSetAvailableBatch_100(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 100)
}

func TestDB_shouldSetAvailableBatch_1000(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 1000)
}

func TestDB_shouldSetAvailableBatch_10000(t *testing.T) {
	test_shouldSetAvailableBatch_n(t, 10000)
}

func test_shouldSetAvailableBatch_n(t *testing.T, n int) {
	ctx := NewContext()
	defer ctx.Cleanup()

	o := newUID()
	d1 := newUID()
	oids := make([]UID, n)
	for i := 0; i < len(oids); i++ {
		oids[i] = o
		o[1]++
	}

	for _, oid := range oids {
		ctx.checkVersions(t, oid)
	}
	b := ctx.db.BeginSetAvailableBatch(d1, 10)
	for i, oid := range oids {
		require.Nil(t, b.SetAvailable(oid, uint64(i+1)))
	}
	require.Nil(t, b.End())

	for i, oid := range oids {
		ctx.checkVersions(t, oid,
			dv(d1, uint64(i+1)),
		)
	}

	// force checkpoint to clear in-memory cache
	require.Nil(t, ctx.db.checkpoint())

	for i, oid := range oids {
		ctx.checkVersions(t, oid,
			dv(d1, uint64(i+1)),
		)
	}
}

func TestDB_shouldSetMixedAvailableBatch_1(t *testing.T) {
	test_shouldSetMixedAvailableBatch_n(t, 1)
}

func TestDB_shouldSetMixedAvailableBatch_10(t *testing.T) {
	test_shouldSetMixedAvailableBatch_n(t, 10)
}

func TestDB_shouldSetMixedAvailableBatch_100(t *testing.T) {
	test_shouldSetMixedAvailableBatch_n(t, 100)
}

func TestDB_shouldSetMixedAvailableBatch_1000(t *testing.T) {
	test_shouldSetMixedAvailableBatch_n(t, 1000)
}

func TestDB_shouldSetMixedAvailableBatch_10000(t *testing.T) {
	test_shouldSetMixedAvailableBatch_n(t, 10000)
}

func test_shouldSetMixedAvailableBatch_n(t *testing.T, n int) {
	ctx := NewContext()
	defer ctx.Cleanup()

	o := newUID()
	d1 := newUID()
	oids := make([]UID, n)
	for i := 0; i < len(oids); i++ {
		oids[i] = o
		o[1]++
	}

	for _, oid := range oids {
		ctx.checkVersions(t, oid)
	}
	b := ctx.db.BeginSetAvailableBatch(d1, 10)
	for i, oid := range oids {
		require.Nil(t, b.SetAvailable(oid, uint64(i+1)))
		if (i % 5) == 4 {
			require.Nil(t, b.SetAvailable(oids[i-4], uint64(0)))
		}
	}
	require.Nil(t, b.End())

	for i, oid := range oids {
		if (i%5) == 0 && i+4 < n {
			ctx.checkVersions(t, oid)
		} else {
			ctx.checkVersions(t, oid,
				dv(d1, uint64(i+1)),
			)
		}
	}

	// force checkpoint to clear in-memory cache
	require.Nil(t, ctx.db.checkpoint())

	for i, oid := range oids {
		if (i%5) == 0 && i+4 < n {
			ctx.checkVersions(t, oid)
		} else {
			ctx.checkVersions(t, oid,
				dv(d1, uint64(i+1)),
			)
		}
	}
}

func TestDB_shouldReloadWAL_1(t *testing.T) {
	test_shouldReloadWAL_n(t, 1)
}

func TestDB_shouldReloadWAL_10(t *testing.T) {
	test_shouldReloadWAL_n(t, 10)
}

func TestDB_shouldReloadWAL_50(t *testing.T) {
	test_shouldReloadWAL_n(t, 50)
}

func TestDB_shouldReloadWAL_100(t *testing.T) {
	test_shouldReloadWAL_n(t, 100)
}

func TestDB_shouldReloadWAL_1000(t *testing.T) {
	test_shouldReloadWAL_n(t, 1000)
}

func TestDB_shouldReloadWAL_10000(t *testing.T) {
	test_shouldReloadWAL_n(t, 10000)
}

func test_shouldReloadWAL_n(t *testing.T, n int) {
	ctx := NewContext()
	defer ctx.Cleanup()

	o := newUID()
	d1 := newUID()
	oids := make([]UID, n)
	for i := 0; i < len(oids); i++ {
		oids[i] = o
		o[1]++
	}

	for _, oid := range oids {
		ctx.checkVersions(t, oid)
	}
	b := ctx.db.BeginSetAvailableBatch(d1, n)
	for i, oid := range oids {
		require.Nil(t, b.SetAvailable(oid, uint64(i+1)))
	}
	require.Nil(t, b.End())

	fmt.Println("check 1")
	for i, oid := range oids {
		ctx.checkVersions(t, oid,
			dv(d1, uint64(i+1)),
		)
	}

	fmt.Println("reopen")
	// NB: MUST close the db first to force bolt to release lock on file
	// TODO: how to test effect of process being killed?
	ctx.db.Close()
	// reopen DB to force WAL load
	db, err := OpenDB(filepath.Join(ctx.dir, "db"))
	if err != nil {
		panic(err)
	}
	ctx.db = db

	fmt.Println("check 2")
	for i, oid := range oids {
		ctx.checkVersions(t, oid,
			dv(d1, uint64(i+1)),
		)
	}
}

func BenchmarkDB_SetAvailableBatch_10_10(b *testing.B) {
	bench_setavailbatch(b, 10, 10)
}

func BenchmarkDB_SetAvailableBatch_10_100(b *testing.B) {
	bench_setavailbatch(b, 10, 100)
}

func BenchmarkDB_SetAvailableBatch_100_10(b *testing.B) {
	bench_setavailbatch(b, 100, 10)
}

func BenchmarkDB_SetAvailableBatch_100_100(b *testing.B) {
	bench_setavailbatch(b, 100, 100)
}

func BenchmarkDB_SetAvailableBatch_1000_10(b *testing.B) {
	bench_setavailbatch(b, 1000, 10)
}

func BenchmarkDB_SetAvailableBatch_1000_100(b *testing.B) {
	bench_setavailbatch(b, 1000, 100)
}

func bench_setavailbatch(b *testing.B, no, nd int) {
	ctx := NewContext()
	defer ctx.Cleanup()

	oids := make([]UID, no)
	for i := 0; i < no; i++ {
		oids[i] = newUID()
	}
	dids := make([]UID, nd)
	for i := 0; i < nd; i++ {
		dids[i] = newUID()
	}

	b.ResetTimer()
	dx := -1
	var batch *Batch
	for i := 0; i < b.N; i++ {
		d := (i / no) % nd
		o := i % no
		if d != dx {
			if batch != nil {
				require.Nil(b, batch.End())
			}
			batch = ctx.db.BeginSetAvailableBatch(dids[d], no)
			dx = d
		}
		require.Nil(b, batch.SetAvailable(oids[o], 42))
	}
	if batch != nil {
		require.Nil(b, batch.End())
	}
	b.StopTimer()
}

func BenchmarkDB_ForAllDevices_empty(b *testing.B) {
	ctx := NewContext()
	defer ctx.Cleanup()

	o1 := newUID()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		ctx.checkVersions(b, o1)
	}
	b.StopTimer()
}

func BenchmarkDB_ForAllDevices_1_cache(b *testing.B) {
	bench_foralldev(b, 1, false)
}

func BenchmarkDB_ForAllDevices_1_db(b *testing.B) {
	bench_foralldev(b, 1, true)
}

func BenchmarkDB_ForAllDevices_10_cache(b *testing.B) {
	bench_foralldev(b, 10, false)
}

func BenchmarkDB_ForAllDevices_10_db(b *testing.B) {
	bench_foralldev(b, 10, true)
}

func BenchmarkDB_ForAllDevices_100_cache(b *testing.B) {
	bench_foralldev(b, 100, false)
}

func BenchmarkDB_ForAllDevices_100_db(b *testing.B) {
	bench_foralldev(b, 100, true)
}

func foralldev_nop(_ UID, _ uint64) bool { return false }

func bench_foralldev(b *testing.B, n int, nocache bool) {
	ctx := NewContext()
	defer ctx.Cleanup()

	o1 := newUID()
	for i := 0; i < n; i++ {
		batch := ctx.db.BeginSetAvailableBatch(newUID(), 1)
		require.Nil(b, batch.SetAvailable(o1, 42))
		require.Nil(b, batch.End())
	}

	if nocache {
		// force checkpoint to clear in-memory cache
		require.Nil(b, ctx.db.checkpoint())
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, err := ctx.db.ForAllDevices(o1, foralldev_nop)
		require.Nil(b, err)
	}
	b.StopTimer()
}

func TestDB_shouldAddSA(t *testing.T) {
	ctx := NewContext()
	defer ctx.Cleanup()

	did := newUID()
	require.False(t, ctx.db.IsSA(did))
	require.Nil(t, ctx.db.AddSA(did))
	require.True(t, ctx.db.IsSA(did))
}

func BenchmarkAddSA_identical(b *testing.B) {
	ctx := NewContext()
	defer ctx.Cleanup()

	did := newUID()
	for i := 0; i < b.N; i++ {
		ctx.db.AddSA(did)
	}
	b.StopTimer()
}

func BenchmarkParallelAddSA_identical(b *testing.B) {
	ctx := NewContext()
	defer ctx.Cleanup()

	did := newUID()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			ctx.db.AddSA(did)
		}
	})
	b.StopTimer()
}

func BenchmarkAddSA_different(b *testing.B) {
	ctx := NewContext()
	defer ctx.Cleanup()

	for i := 0; i < b.N; i++ {
		did := newUID()
		ctx.db.AddSA(did)
	}
	b.StopTimer()
}

func BenchmarkParallelAddSA_different(b *testing.B) {
	ctx := NewContext()
	defer ctx.Cleanup()

	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			did := newUID()
			ctx.db.AddSA(did)
		}
	})
	b.StopTimer()
}
