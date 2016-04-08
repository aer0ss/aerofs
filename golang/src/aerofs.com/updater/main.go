//

package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

var errInvalidManifest error = fmt.Errorf("invalid manifest")

func Usage() {
	log.Printf("Usage:\n")
	log.Printf("%s create <manifest> <store> <src> [<src>:<prefix>...]\n", os.Args[0])
	log.Printf("%s apply  <manifest> <store> <dst> [<previous>]\n", os.Args[0])
	os.Exit(1)
}

func DisplayError(err error) {
	log.Printf("Serving error page on localhost: %s\n", err.Error())
	killServer := make(chan int)

	http.HandleFunc("/",
		func(err error) func(w http.ResponseWriter, r *http.Request) {
			return func(w http.ResponseWriter, r *http.Request) {
				io.WriteString(w, err.Error())
				killServer <- 1
			}
		}(err))

	// Windows requires we explicitly bind to localhost
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		log.Fatalf("Could not start webserver listener: %s\n", err.Error())
	}

	go http.Serve(ln, nil)

	time.Sleep(2 * time.Second)
	if err = OpenErrorPage(ln.Addr().String()); err != nil {
		log.Fatalf("Failed to open browser: %s\n", err.Error())
	}

	select {
	case c := <-killServer:
		os.Exit(c)
	case <-time.After(5 * time.Second):
		log.Fatalf("Error page not served")
	}
}

// OS-specific Aero launcher
func runAsBinary() {
	var abs string
	var err error

	if flag.NArg() == 0 {
		abs, err = filepath.Abs(os.Args[0])
		if err != nil {
			DisplayError(fmt.Errorf("Failed to resolve executable path:\n%s\n", err.Error()))
		}
	} else if flag.NArg() == 2 && flag.Arg(0) == "as" {
		// N.B. we use the path to find site-config etc and the script name to
		// determine gui/cli/etc. This hack passes the correct path and name,
		// since using `exec -a "$0"` will override _both_ of these.
		abs = filepath.Join(filepath.Dir(os.Args[0]), filepath.Base(flag.Arg(1)))
	} else {
		DisplayError(fmt.Errorf("Invalid arguments: %s %s", flag.Arg(0), flag.Arg(1)))
	}

	force := flag.Bool("force", false, "force update")
	if err = LaunchAero(abs, *force); err != nil {
		log.Fatalf("Failed to launch: %s\n", err.Error())
	}
}

func main() {
	flag.Parse()

	if flag.NArg() == 0 || flag.NArg() == 2 {
		runAsBinary()
		return
	}

	if flag.NArg() < 4 {
		Usage()
	}
	op := flag.Arg(0)
	manifestFile := flag.Arg(1)
	data := flag.Arg(2)

	if op == "apply" {
		if flag.NArg() > 5 {
			Usage()
		}
		var src string
		dst := filepath.Clean(flag.Arg(3))
		if flag.NArg() > 4 {
			src = filepath.Clean(flag.Arg(4))
		}
		if src == dst {
			log.Fatalf("in-place apply not supported")
		}
		ApplyManifest(manifestFile, data, src, dst)
	} else if op == "create" {
		CreateManifest(manifestFile, data, flag.Args()[3:])
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

	if err = Apply(src, dst, manifest, fetcher); err != nil {
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
	var err error
	store := &LocalStore{
		BasePath: data,
	}
	manifest := make(Manifest)

	if err = os.MkdirAll(data, 0755); err != nil && !os.IsExist(err) {
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

	d, err := json.Marshal(&manifest)
	if err != nil {
		log.Fatalf("failed to serialize %s: %s\n", manifestFile, err.Error())
	}
	if err = ioutil.WriteFile(manifestFile, d, os.FileMode(0644)); err != nil {
		log.Fatalf("failed to write manifest: %s\n", err.Error())
	}
}
