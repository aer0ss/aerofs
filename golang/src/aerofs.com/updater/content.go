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

type ContentFetcher interface {
	SetFormat(format string) error

	// Fetch retrieves the content corresponding to the given SHA256 hash
	// and returns a path to a local file containing it
	Fetch(hash string) (string, error)
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
	BaseURL   string
	Transport *http.Transport
	TmpDir    string
}

func (f *HttpFetcher) Fetch(hash string) (string, error) {
	c := http.Client{
		Transport: f.Transport,
	}
	log.Printf("Fetching %s\n", hash)
	resp, err := c.Get(f.BaseURL + "/" + hash)
	if err != nil {
		return "", fmt.Errorf("Could not get fetch url:\n%s", err.Error())
	}
	log.Printf("  length %s\n", resp.Header.Get("Content-Length"))
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("Unexpected %s", resp.Status)
	}
	os.MkdirAll(f.TmpDir, 0755)
	tmp := filepath.Join(f.TmpDir, hash)
	file, err := os.Create(tmp)
	if err != nil {
		return "", fmt.Errorf("Could not create temp file:\n%s", err.Error())
	}
	var src io.ReadCloser = resp.Body
	if f.rd != nil {
		if src, err = f.rd(src); err != nil {
			return "", err
		}
	}
	_, err = io.Copy(file, src)
	if f.rd != nil {
		src.Close()
	}
	resp.Body.Close()
	file.Close()
	if err != nil {
		os.Remove(tmp)
		return "", fmt.Errorf("Could not copy update:\n%s", err.Error())
	}
	return tmp, nil
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

func (s *LocalStore) Fetch(hash string) (string, error) {
	src := filepath.Join(s.BasePath, hash)
	tmp := filepath.Join(filepath.Dir(s.BasePath), "tmp-"+hash)
	h, err := CopyFile(tmp, src, s.rd, nil)
	if err != nil {
		os.Remove(tmp)
		return "", fmt.Errorf("Could not copy hash from temp dir:\n%s", err.Error())
	}
	if h != hash {
		os.Remove(tmp)
		return "", fmt.Errorf("Hash mismatch %s != %s", h, hash)
	}
	return tmp, nil
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
