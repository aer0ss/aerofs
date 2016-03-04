// Package lipwig wraps the aerofs lipwig client, providing helper methods and
// handlers for Sloth
package lipwig

import (
	"aerofs.com/sloth/aeroclients/polaris"
	"aerofs.com/sloth/aeroclients/sparta"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/util/asynccache"
	"crypto/sha256"
	"crypto/tls"
	"database/sql"
	"encoding/base64"
	"errors"
	"fmt"
	lipwig "github.com/aerofs/lipwig/client"
	"log"
	"strings"
)

const (
	LIPWIG_ADDR          = "lipwig.service:8787"
	SID_CHAN_BUFFER_SIZE = 128
)

//
// Lipwig Client
//

// Client wraps an aerofs lipwig client
type Client struct {
	client lipwig.Client
}

// SubscribeAndHandlePolaris sets up an SSMP subscription for the given store
// and handles all incoming SSMP events from Polaris
func (c *Client) SubscribeAndHandlePolaris(sid string) error {
	topic := "pol/" + sid
	return c.subscribe(topic)
}

// SubscribeAndHandleAcl sets up an SSMP subscription for the teamserver and
// handles all incoming SSMP events relating to all ACL changes
//
// See SSMPIdentifiers.java for the format of the SSMP topic
func (c *Client) SubscribeAndHandleAcl() error {
	uid := ":2"
	hash := sha256.Sum256([]byte(uid))
	topic := "acl/" + base64.StdEncoding.EncodeToString(hash[:])
	return c.subscribe(topic)
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

	// start syncing based on incoming SSMP events
	go handler.syncTransformsLoop()

	// subscribe to ACL updates
	if err := client.SubscribeAndHandleAcl(); err != nil {
		log.Panic(err)
	}

	// subscribe to Polaris updates for all stores
	for _, sid := range sids {
		if err := client.SubscribeAndHandlePolaris(sid); err != nil {
			log.Panic(err)
		}
	}

	// fetch changes to store membership that have occurred while sloth was down
	handler.syncAcls()

	// fetch new transforms that have occurred while sloth was down
	for _, sid := range sids {
		handler.syncTransforms(sid)
	}

	return client
}

func (c *Client) subscribe(topic string) error {
	log.Print("ssmp: subscribing to ", topic)
	resp, err := c.client.Subscribe(topic)
	if err != nil {
		return err
	}
	if resp.Code != 200 {
		return errors.New(fmt.Sprint("ssmp: subscribe ", topic, " -> ", resp.Code))
	}
	return nil
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
	sidsToSync    chan string
	aclEpoch      uint64
}

func (h *eventHandler) HandleEvent(event lipwig.Event) {
	to := string(event.To)
	log.Print("ssmp: event to ", to)

	// HandleEvent is called synchronously in the lipwig client read loop.
	// Spawn a goroutine for the actual handling.
	if strings.HasPrefix(to, "pol/") {
		h.sidsToSync <- to[4:]
	} else if strings.HasPrefix(to, "acl/") {
		// epoch, err := strconv.ParseUint(string(event.Payload), 10, 64)
		// if err != nil {
		// 	log.Printf("ssmp: non-numeric acl payload: %v\n", string(event.Payload))
		// 	return
		// }
		go h.syncAcls()
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
		sidsToSync:    make(chan string, SID_CHAN_BUFFER_SIZE),
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
