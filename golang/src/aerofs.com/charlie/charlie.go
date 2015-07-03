package main

import (
	"aerofs.com/service"
	"aerofs.com/service/auth"
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/aerofs/httprouter"
	"github.com/boltdb/bolt"
	"net"
	"net/http"
	"os"
	"path"
	"strconv"
	"time"
)

var checkins []byte = []byte("checkins")

func Encode(timestamp int64, ip net.IP) []byte {
	var w bytes.Buffer
	binary.Write(&w, binary.LittleEndian, timestamp)
	// NB: store compact representation for IPv4
	if ip4 := ip.To4(); ip4 != nil {
		binary.Write(&w, binary.LittleEndian, ip4)
	} else {
		binary.Write(&w, binary.LittleEndian, ip)
	}
	return w.Bytes()
}

func Decode(d []byte) (timestamp int64, ip net.IP, err error) {
	r := bytes.NewReader(d)
	err = binary.Read(r, binary.LittleEndian, &timestamp)
	if err != nil {
		return timestamp, ip, err
	}
	ip = make([]byte, r.Len())
	err = binary.Read(r, binary.LittleEndian, &ip)
	return timestamp, ip, err
}

func checkinHandler(w http.ResponseWriter, r *http.Request, c auth.Context, db *bolt.DB) {
	token, ok := c.Token.(auth.DeviceCertToken)
	if !ok {
		http.Error(w, "", http.StatusUnauthorized)
		return
	}

	// we trust nginx to populate this header appropriately
	ip := net.ParseIP(r.Header.Get("X-Real-IP"))
	if ip == nil {
		http.Error(w, "", http.StatusBadRequest)
		return
	}

	k := token.Device()
	v := Encode(time.Now().Unix(), ip)

	// TODO: investigate using Batch and/or a write-ahead-log if random writes are too slow
	err := db.Update(func(tx *bolt.Tx) error {
		b, err := tx.CreateBucketIfNotExists(checkins)
		if err != nil {
			return err
		}
		return b.Put(k[:], v)
	})
	if err == nil {
		http.Error(w, "", http.StatusOK)
	} else {
		fmt.Println("error:", err.Error())
		http.Error(w, "", http.StatusInternalServerError)
	}
}

var errNoCheckin error = fmt.Errorf("no checkin")

func lastCheckinHandler(w http.ResponseWriter, r *http.Request, c auth.Context, db *bolt.DB) {
	k, err := hex.DecodeString(c.Params[0].Value)
	if err != nil || len(k) != 16 {
		fmt.Println("invalid did:", c.Params[0].Value)
		http.Error(w, "", http.StatusNotFound)
		return
	}

	d := make([]byte, 24, 24)
	err = db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket(checkins)
		if b == nil {
			return errNoCheckin
		}
		v := b.Get(k)
		if v == nil {
			return errNoCheckin
		}
		n := copy(d, v)
		d = d[0:n]
		return nil
	})

	if err == errNoCheckin {
		http.Error(w, "", http.StatusNotFound)
		return
	}

	var body []byte
	if err == nil {
		var ts int64
		var ip net.IP
		if ts, ip, err = Decode(d); err == nil {
			body, err = json.Marshal(map[string]string{
				"ip":   ip.String(),
				"time": time.Unix(ts, 0).UTC().Format(time.RFC3339),
			})
		}
	}

	if err != nil {
		fmt.Println("error:", err.Error())
		http.Error(w, "", http.StatusInternalServerError)
	} else {
		w.Header().Set("Content-Type", "application/json")
		w.Write(body)
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

	flag.IntVar(&port, "port", 8701, "listening port")
	flag.StringVar(&dbFile, "db", "/data/charlie/db", "path to database file")
	flag.StringVar(&sharedSecret, "secret", "", "shared secret for service-service auth")

	flag.Parse()

	if len(sharedSecret) == 0 {
		sharedSecret = service.ReadDeploymentSecret()
	}

	err := os.MkdirAll(path.Dir(dbFile), 0600)
	if err != nil {
		panic("failed to create data dir: " + err.Error())
	}

	db, err := bolt.Open(dbFile, 0600, &bolt.Options{
		Timeout: 1 * time.Second,
	})
	if err != nil {
		panic("failed to open database: " + err.Error())
	}

	router := httprouter.New()
	router.GET("/healthcheck", healthCheckHandler)
	router.GET("/checkin/:did", auth.Auth(
		func(w http.ResponseWriter, r *http.Request, c auth.Context) {
			lastCheckinHandler(w, r, c, db)
		},
		auth.NewServiceSharedSecretExtractor(sharedSecret)))
	router.POST("/checkin", auth.Auth(
		func(w http.ResponseWriter, r *http.Request, c auth.Context) {
			checkinHandler(w, r, c, db)
		},
		auth.NewDeviceCertificateExtractor()))

	fmt.Println("Charlie serving at", port)
	err = http.ListenAndServe(":"+strconv.Itoa(port), router)
	if err != nil {
		panic("failed: " + err.Error())
	}
}
