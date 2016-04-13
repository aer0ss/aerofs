package main

import (
	"crypto"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
)

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
		_, err = CopyFile(dst, src)
	}
	return err
}

func UpdateFile(dst, h string, fetcher ContentFetcher) error {
	tmp, err := fetcher.Fetch(h)
	if err != nil {
		return fmt.Errorf("Could not fetch update:\n%s", err.Error())
	}
	if err = os.Rename(tmp, dst); err != nil {
		_, err = CopyFile(dst, tmp)
		os.Remove(tmp)
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

func Apply(src, dst string, manifest map[string]interface{}, fetcher ContentFetcher) error {
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
			err = Apply(sp, dp, cc, fetcher)
		} else {
			var mh string
			var mode os.FileMode
			if mh, mode, err = UnmarshalFile(mc); err != nil {
				return err
			}
			if mode.IsRegular() {
				var ph string
				ph, err = Hash(sp)
				if err != nil || mh != ph {
					err = UpdateFile(dp, mh, fetcher)
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
		}
		if err != nil {
			return fmt.Errorf("Could not apply update:\n%s", err.Error())
		}
	}
	return nil
}
