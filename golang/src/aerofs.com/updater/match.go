package main

import (
	"log"
	"os"
	"path/filepath"
)

func Match(src string, manifest map[string]interface{}) bool {
	var err error
	var sp string

	f, err := os.Open(src)
	if err != nil {
		log.Printf("%s: %s\n", src, err.Error())
		return false
	}
	names, err := f.Readdirnames(-1)
	f.Close()
	if err != nil {
		log.Printf("%s: %s\n", src, err.Error())
		return false
	}

	phy := make(map[string]os.FileMode)
	for _, n := range names {
		if n == "site-config.properties" {
			continue
		}
		fi, err := os.Lstat(filepath.Join(src, n))
		if err != nil {
			log.Printf("%s: %s\n", src, err.Error())
			return false
		}
		phy[n] = fi.Mode()
	}

	match := true
	for n, mc := range manifest {
		sp = filepath.Join(src, n)
		fm, ok := phy[n]
		if !ok {
			match = false
			continue
		}
		delete(phy, n)
		if cc, isDir := mc.(map[string]interface{}); isDir {
			if !fm.IsDir() {
				match = false
				continue
			}
			match = Match(sp, cc) && match
		} else {
			var mh string
			var mode os.FileMode
			if mh, mode, err = UnmarshalFile(mc); err != nil {
				return false
			}
			if mode.IsRegular() {
				if mode != fm {
					match = false
					continue
				}
				var ph string
				ph, err = Hash(sp)
				if err != nil || mh != ph {
					match = false
					continue
				}
			} else if (mode & os.ModeSymlink) != 0 {
				d, err := os.Readlink(sp)
				if err != nil {
					match = false
					continue
				}
				if !filepath.IsAbs(mh) {
					if d != mh {
						match = false
					}
				} else {
					err = errInvalidManifest
				}
			} else {
				err = errInvalidManifest
			}
		}
		if err != nil {
			return false
		}
	}
	if len(phy) > 0 {
		match = false
	}
	return match
}
