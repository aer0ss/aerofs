package main

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type Product int

const (
	Client = iota
	TeamServer
)

const MANIFEST = "manifest.json"

type settings struct {
	approot  string
	rtroot   string
	launcher string
	manifest string
}

func LaunchIfMatching(approot, launcher string, args []string) {
	manifest, err := LoadManifest(filepath.Join(approot, MANIFEST))
	if err != nil {
		log.Printf("Could not load manifest: %s\n", err.Error())
		return
	}

	manifest = manifest["files"].(map[string]interface{})
	if Match(filepath.Join(approot, "current"), manifest) {
		log.Println("Matching approot for manifest")
		if err = Launch(launcher, args); err != nil {
			log.Printf("Failed to launch: %s\n", err.Error())
		}
	}
}

func PreLaunch(launcher string, args []string) error {
	fi, err := os.Stat(launcher)
	if err != nil {
		return fmt.Errorf("No launcher found:\n%s", err.Error())
	}
	if !fi.Mode().IsRegular() {
		return fmt.Errorf("Launcher is not executable")
	}
	return nil
}

func setLogFile(rtroot string) error {
	if err := os.MkdirAll(rtroot, 0755); err != nil {
		return fmt.Errorf("Failed to create rtroot:\n%s", err.Error())
	}

	logpath := filepath.Join(rtroot, "updater.log")
	logfile, err := os.OpenFile(logpath, os.O_APPEND|os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		return fmt.Errorf("Failed to open updater.log for writing:\n%s", err.Error())
	}
	log.SetOutput(logfile)
	return nil
}

func secureTransport(cacert string) (*http.Transport, error) {
	ca := x509.NewCertPool()
	if !ca.AppendCertsFromPEM([]byte(cacert)) {
		return nil, fmt.Errorf("invalid trust anchor: %s", cacert)
	}
	return &http.Transport{
		TLSClientConfig: &tls.Config{
			RootCAs: ca,
		},
	}, nil
}

func Update(config, manifestName, approot string) (string, error) {
	log.Printf("Loading site config from %s\n", config)
	siteConfig, err := LoadJavaProperties(config)
	if err != nil {
		return "", fmt.Errorf("Could not load site-config:\n%s", err.Error())
	}

	url, err := url.Parse(siteConfig["config.loader.configuration_service_url"])
	if err != nil {
		return "", fmt.Errorf("Could not parse site-config:\n%s", err.Error())
	}

	transport, err := secureTransport(siteConfig["config.loader.base_ca_certificate"])
	if err != nil {
		return "", fmt.Errorf("Could not read secure ca certificate:\n%s", err.Error())
	}

	if err := os.MkdirAll(approot, 0755); err != nil {
		return "", fmt.Errorf("Could not create approot:\n%s", err.Error())
	}

	log.Println("Downloading manifest...")
	manifestUrl := url.Scheme + "://" + url.Host + "/static/updates/" + manifestName
	manifestFile := filepath.Join(approot, MANIFEST+".cand")
	manifest, err := Download(manifestUrl, manifestFile, transport)
	if err != nil {
		return "", fmt.Errorf("Could not download manifests:\n%s", err.Error())
	}

	fetcher := &HttpFetcher{
		BaseURL:   url.Scheme + "://" + url.Host + "/static/updates/data",
		Transport: transport,
		TmpDir:    filepath.Join(approot, "dl"),
	}

	format := manifest["format"].(string)
	if err = fetcher.SetFormat(format); err != nil {
		return "", fmt.Errorf("Unsupported data format %s", format)
	}
	manifest = manifest["files"].(map[string]interface{})

	current := filepath.Join(approot, "current")
	version := LastInstallVersion(approot)
	prev := InstallPath(approot, version+1)

	if err = os.Rename(current, prev); err != nil && !os.IsNotExist(err) {
		return "", fmt.Errorf("Could not rename %s -> %s : %s\n", current, prev, err.Error())
	}
	if err = os.MkdirAll(current, 0755); err != nil {
		return "", fmt.Errorf("Could not recursively make %s:\n%s", current, err.Error())
	}

	log.Printf("Applying manifest: %s\n\t%s\n\t%s\n", manifestFile, prev, current)

	if err = Apply(prev, current, manifest, fetcher); err != nil {
		return "", fmt.Errorf("Could not apply updates:\n%s", err.Error())
	}

	log.Println("Copying site-config...")

	// copy site config into new approot
	if err = LinkOrCopy(filepath.Join(current, "site-config.properties"), config); err != nil {
		return "", fmt.Errorf("Could not link or copy site-config:\n%s", err.Error())
	}

	if err = os.Rename(manifestFile, filepath.Join(approot, MANIFEST)); err != nil {
		log.Printf("Failed to rename manifest: %s\n", err.Error())
	}

	log.Println("Launching...")

	return current, nil
}

func InstallPath(approot string, v uint64) string {
	return filepath.Join(approot, "m_"+strconv.FormatUint(v, 16))
}

func LauncherPath(approot string, v uint64, name string) string {
	return filepath.Join(InstallPath(approot, v), name)
}

func LastInstallVersion(approot string) uint64 {
	d, err := os.Open(approot)
	if err != nil {
		log.Printf("no last ver: %s\n", err.Error())
		return 0
	}
	children, err := d.Readdirnames(-1)
	d.Close()
	if err != nil {
		log.Printf("no last ver: %s\n", err.Error())
		return 0
	}
	var mv uint64 = 0
	for _, n := range children {
		if strings.HasPrefix(n, "m_") {
			log.Printf("found ver: %s\n", n)
			if v, err := strconv.ParseUint(n[2:], 16, 64); err == nil && v > mv {
				mv = v
			}
		}
	}

	// cleanup old versions
	for _, n := range children {
		if strings.HasPrefix(n, "m_") {
			if v, err := strconv.ParseUint(n[2:], 16, 64); err == nil && v < mv {
				if err = os.RemoveAll(filepath.Join(approot, n)); err != nil {
					log.Printf("failed to remove %s: %s\n", n, err.Error())
				} else {
					log.Printf("removed %s\n", n)
				}
			}
		}
	}
	return mv
}

func Download(manifestUrl, dest string, transport *http.Transport) (map[string]interface{}, error) {
	c := http.Client{
		Transport: transport,
	}
	resp, err := c.Get(manifestUrl)
	if err != nil {
		return nil, fmt.Errorf("Could not get manifest from appliance:\n%s", err.Error())
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Unexpected response from %s: %s", manifestUrl, resp.Status)
	}
	f, err := os.OpenFile(dest, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0644)
	if err != nil {
		return nil, fmt.Errorf("Could not open manifest destination path:\n%s", err.Error())
	}
	defer f.Close()
	var m map[string]interface{}
	err = json.NewDecoder(io.TeeReader(resp.Body, f)).Decode(&m)
	return m, err
}
