package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"github.com/gorilla/websocket"
	"log"
	"net/http"
)

// implementation struct
type websocketMultiplexingHandler struct {
	broadcaster    broadcast.Broadcaster
	wsUpgrader     websocket.Upgrader
	defaultHandler http.Handler
}

func NewMultiplexingHandler(broadcaster broadcast.Broadcaster, h http.Handler) http.Handler {
	return &websocketMultiplexingHandler{
		wsUpgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
			CheckOrigin:     func(r *http.Request) bool { return true },
		},
		defaultHandler: h,
		broadcaster:    broadcaster,
	}
}

func (h *websocketMultiplexingHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// check to see if websocket upgrade is requested
	if r.Header.Get("Connection") == "Upgrade" && r.Header.Get("Upgrade") == "websocket" {
		handleWebsocket(w, r, &h.wsUpgrader, h.broadcaster)
	} else {
		h.defaultHandler.ServeHTTP(w, r)
	}
}

// infinitely drop incoming messages
func handleIncomingMessages(conn *websocket.Conn, c chan interface{}, broadcaster broadcast.Broadcaster) {
	for {
		_, _, err := conn.ReadMessage()
		if isChanClosedErr(err) {
			broadcaster.Unsubscribe(c)
			close(c)
			return
		} else {
			errors.PanicOnErr(err)
		}
	}
}

func handleWebsocket(w http.ResponseWriter, r *http.Request, upgrader *websocket.Upgrader, broadcaster broadcast.Broadcaster) {
	// upgrade to websocket connection
	conn, err := upgrader.Upgrade(w, r, nil)
	errors.PanicOnErr(err)

	// subscribe to the event stream
	events := broadcaster.Subscribe()

	// handle incoming messages
	go handleIncomingMessages(conn, events, broadcaster)

	// pass all events through the websocket connection
	log.Println("New WS client listening for events...")
	for {
		bytes, more := (<-events).([]byte)
		if !more {
			return
		}
		err = conn.WriteMessage(websocket.TextMessage, bytes)
		if isChanClosedErr(err) {
			broadcaster.Unsubscribe(events)
			return
		} else {
			errors.PanicOnErr(err)
		}
	}
}

func isChanClosedErr(err error) bool {
	_, isCloseError := err.(*websocket.CloseError)
	return isCloseError || err == websocket.ErrCloseSent
}
