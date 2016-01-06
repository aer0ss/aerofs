package main

import (
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/broadcast"
	"github.com/gorilla/websocket"
	"log"
	"net/http"
	"strings"
)

// implementation struct
type websocketMultiplexingHandler struct {
	verifier       auth.TokenVerifier
	broadcaster    broadcast.Broadcaster
	wsUpgrader     websocket.Upgrader
	defaultHandler http.Handler
}

func NewMultiplexingHandler(verifier auth.TokenVerifier, broadcaster broadcast.Broadcaster, h http.Handler) http.Handler {
	return &websocketMultiplexingHandler{
		wsUpgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
			CheckOrigin:     func(r *http.Request) bool { return true },
		},
		defaultHandler: h,
		broadcaster:    broadcaster,
		verifier:       verifier,
	}
}

func (h *websocketMultiplexingHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// check to see if websocket upgrade is requested
	if r.Header.Get("Connection") == "Upgrade" && r.Header.Get("Upgrade") == "websocket" {
		handleWebsocket(w, r, &h.wsUpgrader, h.verifier, h.broadcaster)
	} else {
		h.defaultHandler.ServeHTTP(w, r)
	}
}

// Listen to one incoming message on `conn` for "AUTH <token>" packets.
// Get and return the auth'd uid from `verifier`
func authConnection(conn *websocket.Conn, verifier auth.TokenVerifier) string {
	mtype, data, err := conn.ReadMessage()
	if err != nil {
		log.Print("ws conn %v read err %v\n", conn.UnderlyingConn(), err)
		return ""
	}
	s := string(data)
	if mtype != websocket.TextMessage || !strings.HasPrefix(s, "AUTH ") {
		log.Printf("ws conn %v message not AUTH\n", conn.UnderlyingConn())
		return ""
	}
	token := strings.TrimPrefix(s, "AUTH ")
	uid, err := verifier.VerifyToken(token)
	if err != nil {
		log.Print("err: failed to verify token ", err)
		return ""
	}
	log.Printf("ws conn %v auth by %v\n", conn.UnderlyingConn(), uid)
	return uid
}

func unsubscribeOnClose(conn *websocket.Conn, broadcaster broadcast.Broadcaster, c chan []byte) {
	for {
		_, _, err := conn.NextReader()
		if err != nil {
			log.Printf("ws conn %v closed\n", conn.UnderlyingConn())
			broadcaster.Unsubscribe(c)
			if !isChanClosedErr(err) {
				log.Printf("ws conn %v closed err %v\n", conn.UnderlyingConn(), err)
			}
			return
		}
	}
}

func handleWebsocket(w http.ResponseWriter, r *http.Request, upgrader *websocket.Upgrader, verifier auth.TokenVerifier, broadcaster broadcast.Broadcaster) {
	// upgrade to websocket connection
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Print("websocket upgrade err ", err)
		return
	}
	log.Printf("new ws conn %v\n", conn.UnderlyingConn())

	// authenticate channel, and subscribe to broadcaster
	uid := authConnection(conn, verifier)
	if uid == "" {
		return
	}
	events := broadcaster.Subscribe(uid)
	go unsubscribeOnClose(conn, broadcaster, events)

	// pass all events through the websocket connection
	for {
		bytes, more := <-events
		if !more {
			return
		}
		err = conn.WriteMessage(websocket.TextMessage, bytes)
		if err != nil {
			broadcaster.Unsubscribe(events)
			if !isChanClosedErr(err) {
				log.Printf("ws conn %v write err %v\n", conn.UnderlyingConn(), err)
			}
			return
		}
	}
}

func isChanClosedErr(err error) bool {
	_, isCloseError := err.(*websocket.CloseError)
	return isCloseError || err == websocket.ErrCloseSent
}
