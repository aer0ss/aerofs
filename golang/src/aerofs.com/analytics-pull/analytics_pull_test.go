package main

import (
	"aerofs.com/analytics/db"
	"aerofs.com/analytics/test"
	"aerofs.com/analytics/util"
	"aerofs.com/service/auth"
	"errors"
	"github.com/aerofs/httprouter"
	"github.com/boltdb/bolt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"testing"
	"time"
)

var mockClock = test.NewMockClockImpl(time.Now().UTC())

type MockServiceHTTPClient struct {
	httpServer *httptest.Server
}

func (c *MockServiceHTTPClient) NewRequest(method, targetURL string, body io.Reader) (*http.Request, error) {
	_url, err := url.Parse(c.httpServer.URL)
	if err != nil {
		return nil, errors.New("Failed to parse URL: " + err.Error())
	}
	req, err := http.NewRequest(method, targetURL, body)
	if err != nil {
		return nil, errors.New("Failed to create new mock request: " + err.Error())
	}
	req.URL.Host = _url.Host
	return req, nil
}

func (c *MockServiceHTTPClient) Do(req *http.Request) (*http.Response, error) {
	return http.DefaultClient.Do(req)
}

func initializeRouter(r *httprouter.Router, db *db.BoltKV) {
	r.GET("/stats", auth.OptionalAuth(func(resp http.ResponseWriter, req *http.Request, c auth.Context) {
		resp.Write([]byte(`{"avg_file_count":3,"max_file_count":5,"total_file_size":1000}`))
	}))
	r.GET("/v1.4/stats/*anything", auth.OptionalAuth(func(resp http.ResponseWriter, req *http.Request, c auth.Context) {
		resp.Write([]byte(`{"avg_file_count":3,"max_file_count":5,"total_file_size":1000}`))
	}))
}

func TestAnalytics_Daily_metrics_should_only_send_once_per_interval(t *testing.T) {
	db, testServer := test.SetupTestStructs(setupDB, initializeRouter)
	defer db.Close()
	defer testServer.Close()

	httpclient := &MockServiceHTTPClient{
		httpServer: testServer,
	}

	// NB: make sure any routes needed by getDailyMetrics are properly mocked in initializeRoutes
	err := getDailyMetrics(db, httpclient)
	if err != nil {
		t.Errorf("Error getting daily metrics: %v", err.Error())
	}

	// ensure bucket exists and count number of entries in bucket
	keyCount := 0
	err = db.Update(func(tx *bolt.Tx) error {
		key := util.TimeToBytes(clock.Now().Truncate(DailyMetricsInterval))
		b := tx.Bucket(DailyMetricsKey).Bucket(key)
		if b == nil {
			return errors.New("Daily metrics bucket does not exist")
		}
		// Stats() does not update inside of Update tx, so do it like this
		keyCount = b.Stats().KeyN + 1
		b.Put([]byte("THEMASTERTESTKEY"), []byte("THEMASTERTESTVALUE"))
		return nil
	})
	if err != nil {
		t.Errorf("db error: %v", err.Error())
		return
	}

	// error if any subsequent http requests are made
	handler := http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
		t.Error("HTTP request was made after already getting daily metrics")
	})
	httpclientchecker := &MockServiceHTTPClient{
		httpServer: httptest.NewServer(handler),
	}
	err = getDailyMetrics(db, httpclientchecker)
	if err != nil {
		t.Errorf("Error getting daily metrics: %v", err.Error())
	}

	// ensure bucket exists
	keyCountPrime := 0
	err = db.View(func(tx *bolt.Tx) error {
		key := util.TimeToBytes(clock.Now().Truncate(DailyMetricsInterval))
		b := tx.Bucket(DailyMetricsKey).Bucket(key)
		if b == nil {
			return errors.New("Daily metrics bucket does not exist")
		}
		keyCountPrime = b.Stats().KeyN
		return nil
	})
	if err != nil {
		t.Errorf("db error: %v", err.Error())
		return
	}
	if keyCount != keyCountPrime {
		t.Errorf("Daily metrics bucket was overwritten. Expected keycount to be %v. Got: %v",
			keyCount, keyCountPrime)
	}
}

func TestMain(m *testing.M) {
	// start setup
	clock = mockClock

	// remove timestamp and redirect to stdout for build script compat
	log.SetFlags(0)
	log.SetOutput(os.Stdout)
	log.Println("Test start time:", mockClock.Now())

	//end setup
	//run tests

	os.Exit(m.Run())
}
