package main

import (
	"aerofs.com/service"
	"aerofs.com/service/mysql"
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/broadcast"
	"flag"
	"fmt"
	"github.com/emicklei/go-restful"
	"github.com/emicklei/go-restful/swagger"
	_ "github.com/go-sql-driver/mysql"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"
)

//
// Constants
//

const AuthorizedUser = "Authorized-User"
const BROADCAST_TIMEOUT_SECONDS = 30

//
// Definitions
//

type User struct {
	Id             string  `json:"id"`
	FirstName      string  `json:"firstName"`
	LastName       string  `json:"lastName"`
	TagId          string  `json:"tagId,omitempty"`
	AvatarPath     string  `json:"avatarPath,omitempty"`
	LastOnlineTime *uint64 `json:"lastOnlineTime,omitempty"`
}

type Bot struct {
	Id      string `json:"id"`
	Name    string `json:"name"`
	GroupId string `json:"groupId"`
}

type Group struct {
	Id          string    `json:"id"`
	CreatedTime time.Time `json:"createdTime"`
	Name        string    `json:"name"`
	IsPublic    bool      `json:"isPublic"`
	Members     []string  `json:"members"`
}

type Message struct {
	Id   int64     `json:"id"`
	Time time.Time `json:"time"`
	Body string    `json:"body"`
	From string    `json:"from"`
	To   string    `json:"to"`
}

type LastReadReceipt struct {
	UserId    string    `json:"userId"`
	MessageId int64     `json:"messageId"`
	Time      time.Time `json:"time"`
}

type GroupMemberChange struct {
	UserId   string    `json:"userId"`
	CallerId string    `json:"callerId"`
	Time     time.Time `json:"time"`
	Added    bool      `json:"added"`
}

//
// FIXME: hack until I can figure out read-only keys
//

type UserWritable struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	TagId     string `json:"tagId"`
}

type BotWritable struct {
	Name    string `json:"name"`
	GroupId string `json:"groupId"`
}

type GroupWritable struct {
	Name     *string  `json:"name"`
	IsPublic *bool    `json:"isPublic"`
	Members  []string `json:"members"`
}

type MessageWritable struct {
	Body string `json:"body"`
}

type LastReadReceiptWritable struct {
	MessageId int64 `json:"messageId"`
}

//
// FIXME: hack because array types seem to be borked
//
// go-restful has problems returning an array of structs.
// Returning a struct containing an array of structs is a workaround.
//

type GroupList struct {
	Groups []Group `json:"groups"`
}

type UserList struct {
	Users []User `json:"users"`
}

type BotList struct {
	Bots []Bot `json:"bots"`
}

type MessageList struct {
	Messages []Message `json:"messages"`
}

type IdList struct {
	Ids []string `json:"ids"`
}

type LastReadReceiptList struct {
	LastRead []LastReadReceipt `json:"lastRead"`
}

type GroupMemberHistory struct {
	History []GroupMemberChange `json:"history"`
}

//
// These Events are serialized as json and sent over webscocket connections
//

type Event struct {
	// valid resource values:
	// - "USER"
	// - "USER_AVATAR"
	// - "USER_MESSAGE"
	// - "USER_MESSAGE_READ"
	// - "GROUP"
	// - "GROUP_MESSAGE"
	// - "GROUP_MESSAGE_READ"
	// - "LAST_ONLINE"
	// - "BOT"
	// - "TYPING"
	Resource string `json:"resource"`

	// ID of the modified user or group
	Id string `json:"id"`

	// Optional payload for transmitting additional info
	Payload string `json:"payload,omitempty"`
}

//
// Global variables shared by modules
// TODO: closures for filters which wrap these args
//

// Map of user id to the time they last made a network request
var lastOnlineTimes = make(map[string]time.Time)
var lastOnlineTimesMutex = sync.RWMutex{}

// Shared broadcaster between REST/WS
var broadcaster = broadcast.NewBroadcaster()

// OAuth token verifier
var tokenVerifier auth.TokenVerifier

//
// Main
//

func main() {
	var port int
	var host, dbHost, dbName string
	var swaggerFilePath string
	var verifier string

	// wait for dependent containers
	service.ServiceBarrier()

	// parse command-line args
	flag.StringVar(&host, "host", "localhost", "Host name of the web server")
	flag.IntVar(&port, "port", 8001, "Port upon which the server should listen")
	flag.StringVar(&dbName, "db", "sloth", "MySQL database to use")
	flag.StringVar(&dbHost, "dbHost", "localhost", "MySQL host address")
	flag.StringVar(&swaggerFilePath, "swagger", os.Getenv("HOME")+"/repos/swagger-ui/dist", "Path to swagger-ui files; if missing, swagger will not be invoked.")
	flag.StringVar(&verifier, "verifier", "bifrost", "Token verifier to use. Currently \"bifrost\" or \"echo\"")
	flag.Parse()

	// initialize token verifier
	if verifier == "echo" {
		tokenVerifier = auth.NewEchoTokenVerifier()
	} else {
		tokenVerifier = auth.NewBifrostTokenVerifier()
	}

	// format url strings
	portStr := ":" + strconv.Itoa(port)
	restUrlStr := "http://" + host + portStr

	// connect and run migrations
	db := mysql.CreateConnection(fmt.Sprintf("root@tcp(%v:3306)/", dbHost), dbName)
	defer db.Close()

	// REST routes
	restful.Add(BuildBotsRoutes(db, broadcaster))
	restful.Add(BuildUsersRoutes(db, broadcaster))
	restful.Add(BuildGroupsRoutes(db, broadcaster))
	restful.Add(BuildKeepaliveRoutes())

	// COOOOOOOORRRRRRRRSSSSSSSSS
	restful.Filter(AddCORSHeaders)
	restful.Filter(restful.OPTIONSFilter())

	// Swagger API doc config
	config := swagger.Config{
		WebServices:     restful.DefaultContainer.RegisteredWebServices(),
		WebServicesUrl:  restUrlStr,
		ApiPath:         "/apidocs.json",
		SwaggerPath:     "/apidocs/",
		SwaggerFilePath: swaggerFilePath,
	}
	swagger.RegisterSwaggerService(config, restful.DefaultContainer)

	// listen for REST connections
	log.Print("REST server listening on ", restUrlStr)
	server := &http.Server{
		Addr:    portStr,
		Handler: NewMultiplexingHandler(tokenVerifier, broadcaster, restful.DefaultContainer),
	}
	log.Fatal(server.ListenAndServe())
}
