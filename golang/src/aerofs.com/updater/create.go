package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strconv"
)

/*
{
	"foo" : { ... },
	"bar": [ "755", "<sha256>", "<size>" ],
	"baz": [ "1000000000", "./bar" ],
	...
}
*/

type Manifest map[string]interface{}

func Create(src string, manifest Manifest, store ContentStore) error {
	children, err := ioutil.ReadDir(src)
	if err != nil {
		return fmt.Errorf("Could not find read manifest dir:\n%s", err.Error())
	}
	for _, c := range children {
		n := c.Name()
		p := filepath.Join(src, n)
		if c.IsDir() {
			mc := manifest[n]
			if mc == nil {
				mc = make(Manifest)
				manifest[n] = mc
			}
			cc, ok := mc.(Manifest)
			if !ok {
				return fmt.Errorf("conflicting type at %s", p)
			}
			if err := Create(p, cc, store); err != nil {
				return fmt.Errorf("Could not create store:\n%s", err.Error())
			}
		} else if c.Mode().IsRegular() {
			if manifest[n] != nil {
				log.Printf("WARN: override %s", p)
			}
			h, err := store.Store(p)
			if err != nil {
				return fmt.Errorf("Could not store %s\n%s", p, err.Error())
			}
			manifest[n] = []string{
				strconv.FormatInt(int64(c.Mode()&os.ModePerm), 8),
				h,
				strconv.FormatInt(c.Size(), 16),
			}
		} else if (c.Mode() & os.ModeSymlink) != 0 {
			target, err := os.Readlink(p)
			if err != nil {
				return fmt.Errorf("Could not read link:\n%s", err.Error())
			}
			if filepath.IsAbs(target) {
				return fmt.Errorf("absolute symlink %s -> %s", p, target)
			}
			manifest[n] = []string{
				strconv.FormatInt(int64(os.ModeSymlink), 8),
				target,
			}
		} else {
			return fmt.Errorf("unsupported file mode %s %o", c.Mode(), p, c.Mode())
		}
	}
	return nil
}
