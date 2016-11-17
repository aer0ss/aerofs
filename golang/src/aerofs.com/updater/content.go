package main

import (
	"compress/gzip"
	"compress/zlib"
	"crypto"
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type FetchedContent struct {
	Err error
}

type ContentFetcher interface {
	SetFormat(format string) error

	// Fetch retrieves the content corresponding to the given SHA256 hash
	// and write it to the given dst file
	Fetch(hash, dst string, mode os.FileMode, r chan *FetchedContent)
}

type ContentStore interface {
	// Stores copies the given file into the ContentStore and returns the
	// SHA256 of the copied file
	Store(src string) (string, error)
}

type FormattedStore struct {
	Format string
	rd     func(io.Reader) (io.ReadCloser, error)
	wr     func(io.Writer) io.WriteCloser
}

func (s *FormattedStore) SetFormat(format string) error {
	switch format {
	case "":
		s.rd = nil
		s.wr = nil
	case "gz":
		s.rd = func(r io.Reader) (io.ReadCloser, error) { return gzip.NewReader(r) }
		s.wr = func(w io.Writer) io.WriteCloser { return gzip.NewWriter(w) }
	case "zip":
		s.rd = zlib.NewReader
		s.wr = func(w io.Writer) io.WriteCloser { return zlib.NewWriter(w) }
	default:
		return fmt.Errorf("unknown format: %s", format)
	}
	s.Format = format
	return nil
}

type HttpFetcher struct {
	FormattedStore
	BaseURL string

	c http.Client
}

func NewHttpFetcher(baseUrl string, transport *http.Transport) *HttpFetcher {
	return &HttpFetcher{
		BaseURL: baseUrl,
		c: http.Client{
			Transport: transport,
			Timeout:   120 * time.Second,
		},
	}
}

func (f *HttpFetcher) Fetch(hash, dst string, mode os.FileMode, r chan *FetchedContent) {
	finalizeFetch(f.syncFetch(hash, dst), dst, mode, r)
}

func finalizeFetch(err error, dst string, mode os.FileMode, r chan *FetchedContent) {
	if err != nil {
		os.Remove(dst)
	} else {
		err = os.Chmod(dst, mode)
	}
	r <- &FetchedContent{
		Err: err,
	}
}

func (f *HttpFetcher) syncFetch(hash, dst string) error {
	ref := time.Now().UnixNano()
	resp, err := f.c.Get(f.BaseURL + "/" + hash)
	if err != nil {
		return fmt.Errorf("Could not get fetch url: %s", err.Error())
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Unexpected %s", resp.Status)
	}

	var src io.ReadCloser = resp.Body
	if f.rd != nil {
		if src, err = f.rd(src); err != nil {
			return err
		}
	}
	file, err := os.Create(dst)
	if err != nil {
		return fmt.Errorf("Could not open for writing: %s", err.Error())
	}
	sz, err := io.Copy(file, src)
	elapsed := (time.Now().UnixNano() - ref) / (1000 * 1000)
	file.Close()
	if f.rd != nil {
		src.Close()
	}
	log.Printf("Fetched %s : %s -> %d in %d ms [%d kb/s]",
		hash,
		resp.Header.Get("Content-Length"),
		sz,
		sz/elapsed,
		elapsed)
	return err
}

type LocalStore struct {
	FormattedStore
	BasePath string
}

func NewLocalStore(data string) (*LocalStore, error) {
	var fmt string
	i := strings.IndexByte(data, ':')
	if i != -1 {
		fmt = data[:i]
		data = data[i+1:]
	}
	s := &LocalStore{
		BasePath: data,
	}
	if err := s.SetFormat(fmt); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *LocalStore) Fetch(hash, dst string, mode os.FileMode, r chan *FetchedContent) {
	finalizeFetch(s.syncFetch(hash, dst), dst, mode, r)
}

func (s *LocalStore) syncFetch(hash, dst string) error {
	src := filepath.Join(s.BasePath, hash)
	h, err := CopyFile(dst, src, s.rd, nil)
	if err != nil {
		return fmt.Errorf("Could not copy hash from temp dir:\n%s", err.Error())
	}
	if h != hash {
		return fmt.Errorf("Hash mismatch %s != %s", h, hash)
	}
	return nil
}

func (s *LocalStore) Store(src string) (string, error) {
	suffix := strconv.FormatInt(time.Now().UnixNano(), 16)
	tmp := filepath.Join(filepath.Dir(s.BasePath), "tmp-"+filepath.Base(src)+"-"+suffix)
	h, err := CopyFile(tmp, src, nil, s.wr)
	if err != nil {
		os.Remove(tmp)
		return "", fmt.Errorf("Could not copy file to store:\n%s", err.Error())
	}
	dst := filepath.Join(s.BasePath, h)
	if err = os.Rename(tmp, dst); err != nil {
		return "", fmt.Errorf("Could not rename stored update:\n%s", err.Error())
	}
	return h, nil
}

func CopyFile(dst, src string, rd func(io.Reader) (io.ReadCloser, error), wr func(io.Writer) io.WriteCloser) (string, error) {
	sf, err := os.Open(src)
	if err != nil {
		return "", fmt.Errorf("Could not open %s:\n%s", src, err.Error())
	}
	var sr io.ReadCloser = sf
	if rd != nil {
		if sr, err = rd(sf); err != nil {
			return "", err
		}
	}
	df, err := os.Create(dst)
	if err != nil {
		sf.Close()
		return "", fmt.Errorf("Could not create %s:\n%s", dst, err.Error())
	}
	var dw io.WriteCloser = df
	if wr != nil {
		dw = wr(df)
	}
	h := crypto.SHA256.New()
	io.Copy(io.MultiWriter(dw, h), sr)
	if sr != nil {
		sr.Close()
	}
	if dw != nil {
		dw.Close()
	}
	df.Close()
	sf.Close()
	return hex.EncodeToString(h.Sum([]byte{})), nil
}
