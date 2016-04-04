package main

import (
	"aerofs.com/service"
	"aerofs.com/service/aerotls"
	"aerofs.com/service/auth"
	"crypto/tls"
	"encoding/binary"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/aerofs/httprouter"
	"github.com/aerofs/lipwig/client"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"strconv"
	"strings"
)

type ObjectVersion struct {
	Oid     string `json:"oid"`
	Version uint64 `json:"version"`
}

type StatusRequest struct {
	Objects []ObjectVersion `json:"objects"`
}

type AvailableRequest struct {
	Sid       string          `json:"sid"`
	Available []ObjectVersion `json:"available"`
}

type FilterRequest struct {
	Devices []string `json:"devices"`
}

type context struct {
	db   *DB
	ssmp client.Client
}

func ReadBody(r *http.Request, b interface{}) error {
	d, err := ioutil.ReadAll(r.Body)
	r.Body.Close()
	if err != nil {
		return err
	}
	if err = json.Unmarshal(d, b); err != nil {
		return err
	}
	return nil
}

func WriteJSON(w http.ResponseWriter, k string, v interface{}) {
	d, err := json.Marshal(map[string]interface{}{
		k: v,
	})
	if err != nil {
		http.Error(w, "", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(d)
}

// Given a list of (OIDs, versions) in the body
// Return a list of booleans describing whether the objects are available on a TS/SA
func (c context) getSyncStatus(w http.ResponseWriter, r *http.Request, _ auth.Context) {
	var s StatusRequest
	err := ReadBody(r, &s)
	if err != nil {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	result := make([]bool, len(s.Objects))
	for i, ov := range s.Objects {
		oid, err := NewUIDFromHex(ov.Oid)
		if err != nil {
			http.Error(w, "", http.StatusInternalServerError)
			return
		}
		// TODO: use cache to avoid scan?
		sync, err := c.db.ForAllDevices(oid, func(did UID, version uint64) bool {
			if version == ov.Version && c.db.IsSA(did) {
				return true
			}
			return false
		})
		if err != nil {
			http.Error(w, "", http.StatusInternalServerError)
			return
		}
		result[i] = sync
	}

	WriteJSON(w, "results", result)
}

// Given an OID in the path and an optional list of online DIDs in the body
// Return a list of DIDs which have the OID, sorted by descending advertised version
func (c context) sortFilterByAvailability(w http.ResponseWriter, r *http.Request, a auth.Context) {
	oid, err := NewUIDFromHex(a.Params.ByName("oid"))
	if err != nil {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	var f FilterRequest
	if err := ReadBody(r, &f); err != nil {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	var rd map[UID]struct{}
	if f.Devices != nil && len(f.Devices) > 0 {
		rd := make(map[UID]struct{})
		for _, d := range f.Devices {
			did, err := NewUIDFromHex(d)
			if err != nil {
				http.Error(w, "", http.StatusBadRequest)
				return
			}
			rd[did] = PRESENT
		}
	}

	// abuse SortedVersionMap a little to sort DIDs by version
	sorted := NewSortedVersionMap()
	_, err = c.db.ForAllDevices(oid, func(did UID, version uint64) bool {
		if rd != nil {
			if _, present := rd[did]; !present {
				return false
			}
		}
		// sort by descending version
		sorted.Put(UID([2]uint64{^version, did[0]}), did[1])
		return false
	})
	if err != nil {
		http.Error(w, "", http.StatusInternalServerError)
		return
	}

	// encode sorted result
	available := make([]string, 0, sorted.Size())
	sorted.ForAll(func(d UID, v uint64) bool {
		available = append(available, (UID([2]uint64{d[1], v}).String()))
		return false
	})
	WriteJSON(w, "available", available)
}

func (c context) postAvailableContent(w http.ResponseWriter, r *http.Request, a auth.Context) {
	token, ok := a.Token.(auth.DeviceCertToken)
	if !ok {
		http.Error(w, "", http.StatusUnauthorized)
		return
	}

	user := token.User()
	isSA := user == ":2"
	ddid := token.Device()
	did := NewUIDFromBytes(ddid[:])

	if isSA {
		if err := c.db.AddSA(did); err != nil {
			http.Error(w, "", http.StatusInternalServerError)
			return
		}
	}

	var body AvailableRequest
	if err := ReadBody(r, &body); err != nil {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	n := len(body.Available)
	// notification must fit in a single SSMP payload
	// TODO: allow larger batch and send multiple notif
	if isSA && n > 42 {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	notif := make([]byte, 2+n*24)
	notif[0] = byte(((n*24 - 1) >> 8) & 0x03)
	notif[1] = byte((n*24 - 1) & 0xff)

	result := make([]bool, n)
	// NB: this acquires a write lock on the DB, which *must* be released
	// by calling batch.End()
	batch := c.db.BeginSetAvailableBatch(did, n)
	for i, ov := range body.Available {
		oid, err := NewUIDFromHex(ov.Oid)
		if err == nil {
			err = batch.SetAvailable(oid, ov.Version)
		}
		if err != nil {
			http.Error(w, "", http.StatusBadRequest)
			// NB: ignore error
			batch.End()
			return
		}
		idx := 2 + 24*i
		oid.Encode(notif[idx : idx+16])
		binary.BigEndian.PutUint64(notif[idx+16:idx+24], ov.Version)
		result[i] = true
	}
	if err := batch.End(); err != nil {
		http.Error(w, "", http.StatusInternalServerError)
		return
	}

	WriteJSON(w, "results", result)

	// for havre
	if r, err := c.ssmp.Ucast("havre", string(notif)); err != nil || r.Code != 200 {
		log.Println("failed loc pub", r, err)
	}
	if isSA {
		// for sync status
		if r, err := c.ssmp.Mcast("sync/"+body.Sid, string(notif)); err != nil || r.Code != 200 {
			log.Println("failed sync pub", r, err)
		}
	}
}

func healthCheckHandler(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	http.Error(w, "", http.StatusOK)
}

func main() {
	service.ServiceBarrier()

	var port int
	var dbFile string
	var sharedSecret string
	var ssmpAddress string

	flag.IntVar(&port, "port", 8080, "listening port")
	flag.StringVar(&dbFile, "db", "/data/waldo/db", "path to database file")
	flag.StringVar(&sharedSecret, "secret", "", "shared secret for service-service auth")
	flag.StringVar(&ssmpAddress, "ssmp", "lipwig.service:8787", "")

	flag.Parse()

	if len(sharedSecret) == 0 {
		sharedSecret = service.ReadDeploymentSecret()
	}

	err := os.MkdirAll(path.Dir(dbFile), 0600)
	if err != nil {
		panic("failed to create data dir: " + err.Error())
	}

	db, err := OpenDB(dbFile)
	if err != nil {
		panic("failed to open database: " + err.Error())
	}

	// TODO: auto-reconnection
	cfg := aerotls.NewClientConfig("waldo")
	i := strings.IndexByte(ssmpAddress, ':')
	if i == -1 {
		panic("ssmp address expected as host:port")
	}
	c, err := tls.Dial("tcp", ssmpAddress, cfg)
	if err != nil {
		panic(err)
	}
	client := client.NewClient(c, client.Discard)
	if r, err := client.Login(".", "secret", sharedSecret); err != nil || r.Code != 200 {
		log.Println("failed ssmp login", r)
		panic(err)
	}

	var ctx context
	ctx.db = db
	ctx.ssmp = client

	certAuth := auth.NewDeviceCertificateExtractor()
	secretAuth := auth.NewServiceSharedSecretExtractor(sharedSecret)

	router := httprouter.New()
	router.GET("/healthcheck", healthCheckHandler)
	router.POST("/locations/submit", auth.Auth(
		ctx.postAvailableContent,
		certAuth))
	router.POST("/locations/status", auth.Auth(
		ctx.getSyncStatus,
		certAuth, secretAuth))
	router.POST("/locations/filter/:oid", auth.Auth(
		ctx.sortFilterByAvailability,
		secretAuth))

	fmt.Println("Waldo serving at", port)
	err = http.ListenAndServe(":"+strconv.Itoa(port), router)
	if err != nil {
		panic("failed: " + err.Error())
	}
}
