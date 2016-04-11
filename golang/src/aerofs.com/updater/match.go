package main

import (
	"log"
	"os"
	"path/filepath"
	"runtime"
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
			log.Printf("%s/%s: %s\n", src, n, err.Error())
			return false
		}
		phy[n] = fi.Mode()
	}

	// NB: we only care about the user bits
	// NB: on windows the x bit is lost
	var mask os.FileMode = 0700
	if runtime.GOOS == "windows" {
		mask = 0600
	}

	match := true
	for n, mc := range manifest {
		sp = filepath.Join(src, n)
		fm, ok := phy[n]
		if !ok {
			log.Printf("%s missing\n", sp)
			match = false
			continue
		}
		delete(phy, n)
		if cc, isDir := mc.(map[string]interface{}); isDir {
			if !fm.IsDir() {
				log.Printf("%s not dir\n", sp)
				match = false
				continue
			}
			match = Match(sp, cc) && match
		} else {
			var mh string
			var mode os.FileMode
			if mh, mode, err = UnmarshalFile(mc); err != nil {
				log.Printf("%s: %s\n", sp, err.Error())
				return false
			}
			if mode.IsRegular() {
				if ((mode ^ fm) & mask) != 0 {
					log.Printf("%s mode %d != %d\n", sp, mode, fm)
					match = false
					continue
				}
				var ph string
				ph, err = Hash(sp)
				if err != nil || mh != ph {
					log.Printf("%s hash %s != %s\n", sp, mh, ph)
					match = false
					continue
				}
			} else if (mode & os.ModeSymlink) != 0 {
				d, err := os.Readlink(sp)
				if err != nil {
					log.Printf("%s not symlink: %s\n", sp, err.Error())
					match = false
					continue
				}
				if !filepath.IsAbs(mh) {
					if d != mh {
						log.Printf("%s lnk %s != %s\n", sp, mh, d)
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
			log.Printf("%s: %s\n", sp, err.Error())
			return false
		}
	}
	if len(phy) > 0 {
		log.Printf("%s unexpected children %v\n", src, phy)
		match = false
	}
	return match
}
