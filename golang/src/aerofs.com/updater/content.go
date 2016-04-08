package main

import (
	"crypto"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"time"
)

type ContentFetcher interface {
	// Fetch retrieves the content corresponding to the given SHA256 hash
	// and returns a path to a local file containing it
	Fetch(hash string) (string, error)
}

type ContentStore interface {
	// Stores copies the given file into the ContentStore and returns the
	// SHA256 of the copied file
	Store(src string) (string, error)
}

type HttpFetcher struct {
	BaseURL   string
	Transport *http.Transport
}

func (f *HttpFetcher) Fetch(hash string) (string, error) {
	c := http.Client{
		Transport: f.Transport,
	}
	resp, err := c.Get(f.BaseURL + "/" + hash)
	if err != nil {
		return "", fmt.Errorf("Could not get fetch url:\n%s", err.Error())
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("Unexpected %s", resp.Status)
	}
	tmp := filepath.Join(os.TempDir(), hash)
	os.MkdirAll(os.TempDir(), 0755) // os.TempDir() sometimes doesn't exist on Darwin
	os.Remove(tmp)
	file, err := os.Create(tmp)
	if err != nil {
		return "", fmt.Errorf("Could not create temp file:\n%s", err.Error())
	}
	if _, err = io.Copy(file, resp.Body); err != nil {
		file.Close()
		os.Remove(tmp)
		return "", fmt.Errorf("Could not copy update:\n%s", err.Error())
	}
	file.Close()
	return tmp, nil
}

type LocalStore struct {
	BasePath string
}

func (s *LocalStore) Fetch(hash string) (string, error) {
	src := filepath.Join(s.BasePath, hash)
	tmp := filepath.Join(filepath.Dir(s.BasePath), "tmp-"+hash)
	h, err := CopyFile(tmp, src)
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
	h, err := CopyFile(tmp, src)
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

func CopyFile(dst, src string) (string, error) {
	sf, err := os.Open(src)
	if err != nil {
		return "", fmt.Errorf("Could not open %s:\n%s", src, err.Error())
	}
	df, err := os.Create(dst)
	if err != nil {
		sf.Close()
		return "", fmt.Errorf("Could not create %s:\n%s", dst, err.Error())
	}
	h := crypto.SHA256.New()
	io.Copy(io.MultiWriter(df, h), sf)
	df.Close()
	sf.Close()
	return hex.EncodeToString(h.Sum([]byte{})), nil
}
