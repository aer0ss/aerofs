// Package lipwig wraps the aerofs lipwig client, providing helper methods and
// handlers for Sloth
package lipwig

import (
	"aerofs.com/sloth/aero-clients/polaris"
	"aerofs.com/sloth/aero-clients/sparta"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/util/asynccache"
	"crypto/tls"
	"database/sql"
	"errors"
	"fmt"
	lipwig "github.com/aerofs/lipwig/client"
	"log"
	"strings"
)

const LIPWIG_ADDR = "lipwig.service:8787"

//
// Lipwig Client
//

// Client wraps an aerofs lipwig client
type Client struct {
	client lipwig.Client
}

// SubscribeAndHandle sets up an SSMP subscription to the given store, and
// handles all incoming SSMP events
func (c *Client) SubscribeAndHandle(sid string) error {
	topic := "pol/" + sid
	log.Print("ssmp: subscribing to ", topic)
	resp, err := c.client.Subscribe(topic)
	if err != nil {
		return err
	}
	if resp.Code != 200 {
		return errors.New(fmt.Sprint("ssmp: subscribe ", sid, " -> ", resp.Code))
	}
	return nil
}

// Start creates a new connection to lipwig, subscribes to all relevant
// channels, and returns the Client struct
func Start(
	tlsConfig *tls.Config,
	db *sql.DB,
	deploymentSecret string,
	polarisClient *polaris.Client,
	spartaClient *sparta.Client,

) *Client {

	conn := getConn(tlsConfig)
	sids := getSidsToFollow(db)
	handler := newEventHandler(db, polarisClient, spartaClient)

	client := &Client{
		client: lipwig.NewClient(conn, handler),
	}

	resp, err := client.client.Login("sloth", "secret", deploymentSecret)
	if err != nil {
		log.Panic(err)
	}
	if resp.Code != 200 {
		log.Panic("ssmp: login failed ", resp)
	}

	for _, sid := range sids {
		if err := client.SubscribeAndHandle(sid); err != nil {
			log.Panic(err)
		}
	}

	return client
}

//
// Main Event Handler
//

// eventHandler implements lipwig.EventHandler
type eventHandler struct {
	db            *sql.DB
	didOwners     asynccache.Map // map of DID -> UID
	polarisClient *polaris.Client
	spartaClient  *sparta.Client
}

func (h *eventHandler) HandleEvent(event lipwig.Event) {
	to := string(event.To)
	log.Print("ssmp: event to ", to)

	// HandleEvent is called synchronously in the lipwig client read loop.
	// Spawn a goroutine for the actual handling.
	if strings.HasPrefix(to, "pol/") {
		sid := to[4:]
		go h.handleTransformEvent(sid)
	} else {
		log.Print("ssmp: unknown channel ", to)
	}
}

func newEventHandler(
	db *sql.DB,
	polarisClient *polaris.Client,
	spartaClient *sparta.Client,

) *eventHandler {

	return &eventHandler{
		db:            db,
		didOwners:     asynccache.New(spartaClient.GetDeviceOwner),
		polarisClient: polarisClient,
		spartaClient:  spartaClient,
	}
}

//
// Utility methods
//

func getConn(cfg *tls.Config) *tls.Conn {
	log.Print("ssmp: connecting to ", LIPWIG_ADDR)
	conn, err := tls.Dial("tcp", LIPWIG_ADDR, cfg)
	if err != nil {
		log.Panic(err)
	}
	return conn
}

func getSidsToFollow(db *sql.DB) []string {
	tx := dao.BeginOrPanic(db)
	defer tx.Rollback()
	return dao.GetGroupSids(tx)
}
