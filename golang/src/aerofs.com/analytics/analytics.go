package main

import (
	"encoding/json"
	"io/ioutil"
	"log"
	"net/http"
	"sync"
	"time"
)

type Event struct {
	Event      string    `json:"event"`
	CustomerID string    `json:"customer_id"`
	Timestamp  time.Time `json:"timestamp"`
	Value      int       `json:"value"`
}

// simple map suitable for concurrent use
type EventStore struct {
	m    map[string]int
	lock sync.Mutex
}

func (es *EventStore) Get(k string) (int, bool) {
	es.lock.Lock()
	defer es.lock.Unlock()
	val, ok := es.m[k]
	return val, ok
}
func (es *EventStore) Increment(k string, v int) {
	es.lock.Lock()
	defer es.lock.Unlock()
	es.m[k] = es.m[k] + v
}
func (es *EventStore) CopyBaseMap() map[string]int {
	es.lock.Lock()
	defer es.lock.Unlock()
	mcopy := make(map[string]int)
	for k, v := range es.m {
		mcopy[k] = v
	}

	return mcopy
}
func (es *EventStore) CopyBaseMapAndReset() map[string]int {
	es.lock.Lock()
	defer es.lock.Unlock()
	mcopy := make(map[string]int)
	for k, v := range es.m {
		mcopy[k] = v
	}

	es.m = make(map[string]int)

	return mcopy
}

func createJsonEvent(k string, v int, t time.Time) (string, error) {
	e := Event{k, "customer_id", t, v}

	b, err := json.Marshal(e)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func persistLoop(ch <-chan map[string]int) {
	// write event map to disk when received
	// TODO: better way to do this?
	for m := range ch {
		s, err := json.Marshal(m)
		if err != nil {
			log.Println(err)
		}

		err = ioutil.WriteFile("eventmapbackup", []byte(s), 0644)
		if err != nil {
			log.Println(err)
		}
	}
}

func readEventFromRequestBody(req *http.Request) (string, error) {
	b, err := ioutil.ReadAll(req.Body)
	if err != nil {
		return "", err
	}

	s := string(b)
	return s, nil
}

func sendEventsLoop(es *EventStore, ch chan<- map[string]int) {
	t := time.NewTicker(5 * time.Second)
	// send current aggregated events on timer tick
	for range t.C {
		// TODO: avoid erasing before a failed send
		// sending events, so erase map so we don't resend
		m := es.CopyBaseMapAndReset()

		// create JSON events for each map entry
		for k, v := range m {
			s, err := createJsonEvent(k, v, time.Now())
			if err != nil {
				log.Println("Failed to create JSON event: %s", err)
				continue
			}
			log.Printf("Sending %s\n", s)
		}

		ch <- es.CopyBaseMap()
	}
}

func main() {
	es := &EventStore{m: make(map[string]int)}
	ch := make(chan map[string]int)

	//go persistLoop(ch)
	//go sendEventsLoop(es, ch)

	http.HandleFunc("/events", func(resp http.ResponseWriter, req *http.Request) {
		s, err := readEventFromRequestBody(req)
		if err != nil {
			log.Println(err)
		}

		es.Increment(s, 1)
		mcopy := es.CopyBaseMap()
		ch <- mcopy
	})

	err := http.ListenAndServe(":9400", nil)
	if err != nil {
		log.Fatal("ListenAndServe: ", err)
	}
}
