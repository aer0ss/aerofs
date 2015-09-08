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
	"time"
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
	if err := os.MkdirAll(approot, 0755); err != nil {
		log.Printf("Could not mkdir approot: %s", err.Error())
		return
	}

	manifest, err := LoadManifest(filepath.Join(approot, MANIFEST))
	if err != nil {
		log.Printf("Could not load manifest: %s", err.Error())
		return
	}

	if Match(filepath.Join(approot, "current"), manifest) {
		if err = Launch(launcher, args); err != nil {
			log.Printf(err.Error())
			DisplayError(fmt.Errorf("Could not launch:\n%s\n", err.Error()))
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
	logpath := filepath.Join(rtroot, "updater.log")
	logfile, err := os.OpenFile(logpath, os.O_APPEND | os.O_CREATE | os.O_RDWR, 0644)
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

func Update(config, manifestName, approot string) error {
	siteConfig, err := LoadJavaProperties(config)
	if err != nil {
		return fmt.Errorf("Could not load site-config:\n%s", err.Error())
	}

	url, err := url.Parse(siteConfig["config.loader.configuration_service_url"])
	if err != nil {
		return fmt.Errorf("Could not parse site-config:\n%s", err.Error())
	}

	transport, err := secureTransport(siteConfig["config.loader.base_ca_certificate"])
	if err != nil {
		return fmt.Errorf("Could not read secure ca certificate:\n%s", err.Error())
	}

	manifestUrl := url.Scheme + "://" + url.Host + "/static/updates/" + manifestName
	manifestFile := filepath.Join(approot, MANIFEST)
	manifest, err := Download(manifestUrl, manifestFile, transport)
	if err != nil {
		return fmt.Errorf("Could not download manifests:\n%s", err.Error())
	}

	fetcher := &HttpFetcher{
		BaseURL:   url.Scheme + "://" + url.Host + "/static/updates/data",
		Transport: transport,
	}

	current := filepath.Join(approot, "current")
	next := filepath.Join(approot, "next")

	if err = os.RemoveAll(next); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("Could not recursively remove %s:\n%s", next, err.Error())
	}
	if err = os.MkdirAll(next, 0755); err != nil {
		return fmt.Errorf("Could not recursively make %s:\n%s", next, err.Error())
	}

	if err = Apply(current, next, manifest, fetcher); err != nil {
		return fmt.Errorf("Could not apply updates:\n%s", err.Error())
	}

	// copy site config into new approot
	if err = LinkOrCopy(filepath.Join(next, "site-config.properties"), config); err != nil {
		return fmt.Errorf("Could not link or copy site-config:\n%s", err.Error())
	}

	if _, err = os.Stat(current); err == nil {
		old := filepath.Join(approot, "old-"+strconv.FormatInt(time.Now().UnixNano(), 16))
		if err = os.Rename(current, old); err != nil {
			return fmt.Errorf("Could not rename approot:\n%s", err.Error())
		}
	} else if !os.IsNotExist(err) {
		return fmt.Errorf("Could not find approot:\n%s", err.Error())
	}
	return os.Rename(next, current)
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
