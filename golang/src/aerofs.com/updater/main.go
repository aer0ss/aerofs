//

package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strings"
)

var buildtime string = "unknown"
var commithash string = "unknown"

var errInvalidManifest error = fmt.Errorf("invalid manifest")

func Usage() {
	log.Printf("Usage:\n")
	log.Printf("%s create <manifest> <store> <src> [<src>:<prefix>...]\n", os.Args[0])
	log.Printf("%s apply  <manifest> <store> <dst> [<previous>]\n", os.Args[0])
	os.Exit(1)
}

// OS-specific Aero launcher
func runAsBinary() {
	var abs string
	var err error
	var args []string

	// special invocation form on Linux for cli/gui distinction and sh parameters
	if len(os.Args) > 2 && os.Args[1] == "as" {
		// N.B. we use the path to find site-config etc and the script name to
		// determine gui/cli/etc. This hack passes the correct path and name,
		// since using `exec -a "$0"` will override _both_ of these.
		abs = filepath.Join(filepath.Dir(os.Args[0]), filepath.Base(os.Args[2]))
		if len(os.Args) > 3 {
			args = os.Args[3:]
		}
	} else {
		// ignore parameters otherwise
		// NB: on OSX, invocation from the app bundle sometimes result in the
		// Process Serial Number being passed as a command line parameter.
		abs, err = filepath.Abs(os.Args[0])
		if err != nil {
			abs = os.Args[0]
		}
	}

	if err = LaunchAero(abs, args); err != nil {
		log.Fatalf("Failed to launch: %s\n", err.Error())
		// TODO: dialog box
		// - OSX: osascript
		// - Windows: MessageBox() from user32.dll
		// - Linux: xmessage
	}
}

func main() {
	argc := len(os.Args)

	if argc == 2 && os.Args[1] == "version" {
		fmt.Printf("Commit Hash: %s\n", commithash)
		fmt.Printf("Build Time : %s\n", buildtime)
		return
	}

	if argc < 2 || (os.Args[1] != "create" && os.Args[1] != "apply") {
		runAsBinary()
		return
	}

	if argc < 5 {
		Usage()
	}
	op := os.Args[1]
	manifestFile := os.Args[2]
	data := os.Args[3]

	if op == "apply" {
		if argc > 6 {
			Usage()
		}
		var src string
		dst := filepath.Clean(os.Args[4])
		if argc > 5 {
			src = filepath.Clean(os.Args[5])
		}
		if src == dst {
			log.Fatalf("in-place apply not supported")
		}
		ApplyManifest(manifestFile, data, src, dst)
	} else if op == "create" {
		CreateManifest(manifestFile, data, os.Args[4:])
	} else {
		Usage()
	}
}

func LoadManifest(manifestFile string) (Manifest, error) {
	d, err := ioutil.ReadFile(manifestFile)
	if err != nil {
		return nil, fmt.Errorf("Could not read manifest:\n%s", err.Error())
	}
	var manifest Manifest
	if err = json.Unmarshal(d, &manifest); err != nil {
		return nil, fmt.Errorf("Could not unmarshal manifest:\n%s", err.Error())
	}
	return manifest, nil
}

func ApplyManifest(manifestFile, data, src, dst string) {
	var fetcher ContentFetcher
	if strings.HasPrefix(data, "http://") {
		fetcher = &HttpFetcher{
			BaseURL: data,
		}
	} else {
		fetcher = &LocalStore{
			BasePath: data,
		}
	}

	manifest, err := LoadManifest(manifestFile)
	if err != nil {
		log.Fatal("failed to read manifest: %s\n", err.Error())
	}
	fetcher.SetFormat(manifest["format"].(string))
	manifest = manifest["files"].(map[string]interface{})

	_, err = os.Stat(dst)
	if err == nil {
		if children, err := ioutil.ReadDir(dst); err != nil || len(children) > 0 {
			log.Fatalf("destination exists and is not an empty directory")
		}
	} else if !os.IsNotExist(err) {
		log.Fatalf("failed to stat destination: %s\n", err.Error())
	} else if err = os.MkdirAll(dst, 0755); err != nil && !os.IsExist(err) {
		log.Fatalf("fail to create destination: %s\n", err.Error())
	}
	pa := NewPendingApply(nil)
	err = Apply(src, dst, manifest, pa)
	if err == nil {
		err = pa.FetchMissing(fetcher)
	}
	if err != nil {
		log.Fatalf("failed to apply manifest: %s\n", err.Error())
	}
}

func SubManifestAt(m Manifest, path string) (Manifest, error) {
	idx := 0
	for idx < len(path) {
		next := strings.IndexByte(path[idx:], '/')
		if next == -1 {
			next = len(path)
		}
		n := path[idx:next]
		c := m[n]
		if c == nil {
			c = make(Manifest)
			m[n] = c
		}
		var ok bool
		if m, ok = c.(Manifest); !ok {
			return nil, fmt.Errorf("conflicting type at %s", path[:next])
		}
		idx = next + 1
	}
	return m, nil
}

func CreateManifest(manifestFile, data string, srcs []string) {
	store, err := NewLocalStore(data)
	if err != nil {
		log.Fatalf("Invalid store: %s\n", err.Error())
	}
	manifest := make(Manifest)

	if err = os.MkdirAll(store.BasePath, 0755); err != nil && !os.IsExist(err) {
		log.Fatalf("fail to create data store: %s\n", err.Error())
	}

	for _, src := range srcs {
		m := manifest
		idx := strings.IndexByte(src, ':')
		if idx != -1 {
			if m, err = SubManifestAt(m, src[idx+1:]); err != nil {
				log.Fatalf("%s\n", err.Error())
			}
			src = src[:idx]
		}
		if err = Create(src, m, store); err != nil {
			log.Fatalf("failed to create %s: %s\n", manifestFile, err.Error())
		}
	}

	d, err := json.Marshal(map[string]interface{}{
		"format": store.Format,
		"files":  &manifest,
	})
	if err != nil {
		log.Fatalf("failed to serialize %s: %s\n", manifestFile, err.Error())
	}
	if err = ioutil.WriteFile(manifestFile, d, os.FileMode(0644)); err != nil {
		log.Fatalf("failed to write manifest: %s\n", err.Error())
	}
}
