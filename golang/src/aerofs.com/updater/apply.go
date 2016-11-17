package main

import (
	"crypto"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"sync"
)

type PendingApply struct {
	monitor ProgressMonitor

	c chan *FetchedContent
	d sync.WaitGroup

	w sync.WaitGroup
	e error
	n int
}

func NewPendingApply(monitor ProgressMonitor) *PendingApply {
	return &PendingApply{
		monitor: monitor,
		c:       make(chan *FetchedContent, 10),
	}
}

func (p *PendingApply) Start() {
	p.w.Add(1)
	go func() {
		for {
			c := <-p.c
			if c == nil {
				break
			}
			p.d.Done()
			if p.monitor != nil {
				p.monitor.IncrementProgress(1)
			}
			if c.Err == nil {
				continue
			}
			if p.e == nil {
				p.e = c.Err
			} else {
				p.n++
			}
		}
		p.w.Done()
	}()
}

func (p *PendingApply) Wait() error {
	// wait for all async dl to complete
	p.d.Wait()
	// signal above goroutine to exit
	close(p.c)
	// wait for above goroutine above to exit
	p.w.Wait()
	if p.n > 0 {
		return fmt.Errorf("%d errors. first: %s", p.n+1, p.e.Error())
	}
	return p.e
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

func Apply(src, dst string, manifest map[string]interface{}, fetcher ContentFetcher, pa *PendingApply) error {
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
			err = Apply(sp, dp, cc, fetcher, pa)
		} else {
			var mh string
			var mode os.FileMode
			if mh, mode, err = UnmarshalFile(mc); err != nil {
				return err
			}
			err = ApplyFile(sp, dp, mh, mode, fetcher, pa)
		}
		if err != nil {
			return fmt.Errorf("Could not apply update:\n%s", err.Error())
		}
	}
	return nil
}

func ApplyFile(sp, dp, mh string, mode os.FileMode, fetcher ContentFetcher, pa *PendingApply) error {
	var err error
	// keep track of file
	pa.d.Add(1)
	if mode.IsRegular() {
		var ph string
		ph, err = Hash(sp)
		if err != nil || mh != ph {
			// TODO: cap number of concurrent dl?
			go fetcher.Fetch(mh, dp, mode, pa.c)
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
	// trigger progress monitor
	pa.c <- &FetchedContent{Err: err}
	return err
}
