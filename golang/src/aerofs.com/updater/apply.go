package main

import (
	"crypto"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"sync/atomic"
)

type PendingApply struct {
	monitor ProgressMonitor

	pending PendingContentList

	i int32

	c chan *FetchedContent
}

type PendingContent struct {
	sz   int64
	h    string
	dst  string
	mode os.FileMode
}

type PendingContentList []*PendingContent

func (l PendingContentList) Len() int           { return len(l) }
func (l PendingContentList) Less(i, j int) bool { return l[i].sz > l[j].sz }
func (l PendingContentList) Swap(i, j int)      { l[i], l[j] = l[j], l[i] }

const MaxConcurrentFetch = 8

func NewPendingApply(monitor ProgressMonitor) *PendingApply {
	return &PendingApply{
		monitor: monitor,
		pending: make(PendingContentList, 0),
		c:       make(chan *FetchedContent, MaxConcurrentFetch),
	}
}

func (p *PendingApply) Enqueue(h string, sz int64, dst string, mode os.FileMode) {
	p.pending = append(p.pending, &PendingContent{
		sz:   sz,
		h:    h,
		dst:  dst,
		mode: mode,
	})
}

func (p *PendingApply) fetchWorker(fetcher ContentFetcher) {
	for {
		i := atomic.AddInt32(&p.i, 1)
		if int(i) > len(p.pending) {
			break
		}
		c := p.pending[i-1]
		fetcher.Fetch(c.h, c.dst, c.mode, p.c)
	}
}

func (p *PendingApply) FetchMissing(fetcher ContentFetcher) error {
	// sort dl by descending size
	sort.Sort(p.pending)

	// start dl workers
	for i := 0; i < MaxConcurrentFetch; i++ {
		go p.fetchWorker(fetcher)
	}

	// wait for all dl to complete
	n := len(p.pending)
	for i := 0; i < n; i++ {
		c := <-p.c
		// abort on first error
		if c.Err != nil {
			atomic.StoreInt32(&p.i, int32(n))
			return c.Err
		}
		p.monitor.IncrementProgress(1)
	}

	return nil
}

func Hash(path string) (string, error) {
	fi, err := os.Stat(path)
	if err != nil {
		return "", fmt.Errorf("Could not inspect %s:\n%s", path, err.Error())
	}
	if !fi.Mode().IsRegular() {
		return "", fmt.Errorf("%s is not a regular file", path)
	}
	f, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("Could not open %s:\n%s", path, err.Error())
	}
	defer f.Close()
	h := crypto.SHA256.New()
	if _, err = io.Copy(h, f); err != nil {
		return "", fmt.Errorf("Could not copy hash:\n%s", err.Error())
	}
	return hex.EncodeToString(h.Sum([]byte{})), nil
}

func LinkOrCopy(dst, src string) (err error) {
	if err = os.Link(src, dst); err != nil {
		_, err = CopyFile(dst, src, nil, nil)
	}
	return err
}

func UnmarshalFile(mc interface{}) (string, os.FileMode, error) {
	meta, ok := mc.([]interface{})
	if !ok || len(meta) < 2 {
		return "", 0, errInvalidManifest
	}
	var mode int64
	m, ok := meta[0].(string)
	if !ok {
		return "", 0, errInvalidManifest
	}
	mode, err := strconv.ParseInt(m, 8, 32)
	if err != nil {
		return "", 0, fmt.Errorf("Could not convert file mode:\n%s", err.Error())
	}
	h, ok := meta[1].(string)
	if !ok {
		return "", 0, errInvalidManifest
	}
	return h, os.FileMode(mode), nil
}

// return negative value if size not present or invalid
func UnmarshalFileSize(mc interface{}) int64 {
	if meta, ok := mc.([]interface{}); ok && len(meta) >= 3 {
		if s, ok := meta[2].(string); ok {
			if sz, err := strconv.ParseInt(s, 16, 64); err == nil {
				return sz
			}
		}
	}
	return -1
}

func Apply(src, dst string, manifest map[string]interface{}, pa *PendingApply) error {
	var err error
	var sp, dp string

	for n, mc := range manifest {
		if len(src) > 0 {
			sp = filepath.Join(src, n)
		}
		if len(dst) > 0 {
			dp = filepath.Join(dst, n)
		}
		if cc, isDir := mc.(map[string]interface{}); isDir {
			if err = os.Mkdir(dp, 0755); err != nil {
				return err
			}
			err = Apply(sp, dp, cc, pa)
		} else {
			err = ApplyFile(sp, dp, mc, pa)
		}
		if err != nil {
			return fmt.Errorf("Could not apply update:\n%s", err.Error())
		}
	}
	return nil
}

func ApplyFile(sp, dp string, mc interface{}, pa *PendingApply) error {
	var err error
	var mh string
	var mode os.FileMode
	if mh, mode, err = UnmarshalFile(mc); err != nil {
		return err
	}
	if mode.IsRegular() {
		var ph string
		ph, err = Hash(sp)
		if err != nil || mh != ph {
			sz := UnmarshalFileSize(mc)
			pa.Enqueue(mh, sz, dp, mode)
			return nil
		} else {
			err = LinkOrCopy(dp, sp)
		}
		if err == nil {
			err = os.Chmod(dp, mode)
		}
	} else if (mode & os.ModeSymlink) != 0 {
		if !filepath.IsAbs(mh) {
			err = os.Symlink(mh, dp)
		} else {
			err = errInvalidManifest
		}
	} else {
		err = errInvalidManifest
	}
	pa.monitor.IncrementProgress(1)
	return err
}
