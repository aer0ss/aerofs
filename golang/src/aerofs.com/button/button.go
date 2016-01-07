package main

import (
	"aerofs.com/service/mysql"
	"database/sql"
	"encoding/json"
	"github.com/Coccodrillo/apns"
	"github.com/aerofs/httprouter"
	_ "github.com/go-sql-driver/mysql"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strings"
)

const APNS_DEV_ADDR = "gateway.sandbox.push.apple.com:2195"
const APNS_DEV_PEM_PATH = "/certs/apns.dev.pem"
const BUTTON_PORT = "8095"

type context struct {
	db      *sql.DB
	apnsDev apns.APNSClient
}

type RegistrationRequest struct {
	DeviceType string
	Token      string
	Alias      string
	Dev        bool // defaults to false
}

type NotificationRequest struct {
	Aliases []string
	Body    string
	Badge   int
}

func main() {
	files, _ := ioutil.ReadDir("/")
	for _, f := range files {
		log.Println(f)
	}
	if _, err := os.Stat(APNS_DEV_PEM_PATH); err != nil {
		panic("file not found: " + APNS_DEV_PEM_PATH)
	}

	dbDSN := "root@tcp(mysql.service:3306)/"
	dbName := "button"
	dbParams := "charset=utf8mb4"
	db := mysql.CreateConnectionWithParams(dbDSN, dbName, dbParams)

	ctx := &context{
		db:      db,
		apnsDev: apns.NewClient(APNS_DEV_ADDR, APNS_DEV_PEM_PATH, APNS_DEV_PEM_PATH),
	}

	router := httprouter.New()
	router.GET("/status", handleStatus)
	router.POST("/registration", ctx.handleRegistration)
	router.POST("/notification", ctx.handleNotification)

	addr := ":" + BUTTON_PORT
	log.Println("Listening on " + addr)
	http.ListenAndServe(addr, router)
}

//
// Handlers
//

func handleStatus(response http.ResponseWriter, request *http.Request, _ httprouter.Params) {
	log.Println("/status -> 204")
	response.WriteHeader(204)
}

func (ctx *context) handleRegistration(response http.ResponseWriter, request *http.Request, _ httprouter.Params) {
	bytes, err := ioutil.ReadAll(request.Body)
	if err != nil {
		log.Printf("/registration err: %v\n", err)
		response.WriteHeader(500)
		return
	}

	log.Printf("/registration %v\n", string(bytes))
	r := new(RegistrationRequest)
	err = json.Unmarshal(bytes, r)
	if err != nil {
		log.Print(err)
	}
	if err != nil || isInvalidRegistrationRequest(r) {
		response.WriteHeader(400)
		return
	}

	query := "INSERT INTO registrations (deviceType, token, alias, dev) VALUES (?,?,?,?)"
	_, err = ctx.db.Exec(query, r.DeviceType, r.Token, r.Alias, r.Dev)
	if err != nil {
		log.Print(err)
		response.WriteHeader(500)
		return
	}

	response.WriteHeader(201)
}

func (ctx *context) handleNotification(response http.ResponseWriter, request *http.Request, _ httprouter.Params) {
	bytes, err := ioutil.ReadAll(request.Body)
	if err != nil {
		log.Printf("/notification err: %v\n", err)
		response.WriteHeader(500)
		return
	}

	log.Printf("/notification %v\n", string(bytes))
	r := new(NotificationRequest)
	err = json.Unmarshal(bytes, r)
	if err != nil {
		log.Print(err)
	}
	if err != nil || isInvalidNotificationRequest(r) {
		response.WriteHeader(400)
		return
	}

	apnsDevTokens, _, _, err := getTokensForAliases(ctx.db, r.Aliases)
	if err != nil {
		log.Print(err)
		response.WriteHeader(500)
		return
	}
	numTokens := len(apnsDevTokens)

	log.Printf("sending %v notifications through dev APNS\n", len(apnsDevTokens))
	responses := make([]chan *apns.PushNotificationResponse, 0)
	for _, token := range apnsDevTokens {
		r := sendAPNS(ctx.apnsDev, token, r.Body, r.Badge)
		responses = append(responses, r)
	}
	for _, c := range responses {
		r := <-c
		// if any push fails, return 502
		if !r.Success {
			log.Printf("apns err: %v %v\n", r.AppleResponse, r.Error)
			response.WriteHeader(502)
			return
		}
	}
	log.Printf("successfully sent %v push notifications\n", numTokens)
}

//
// Helpers
//

func isInvalidRegistrationRequest(r *RegistrationRequest) bool {
	if r.DeviceType != "android" && r.DeviceType != "ios" {
		return true
	} else if r.Token == "" || r.Alias == "" {
		return true
	}
	return false
}

func isInvalidNotificationRequest(r *NotificationRequest) bool {
	return len(r.Aliases) == 0 || len(r.Body) == 0
}

func toSliceOfInterface(ss []string) []interface{} {
	si := make([]interface{}, 0)
	for _, s := range ss {
		si = append(si, interface{}(s))
	}
	return si
}

func getTokensForAliases(db *sql.DB, aliases []string) (apnsDev, apnsProd, gcmProd []string, err error) {
	conditions := make([]string, 0)
	for _, _ = range aliases {
		conditions = append(conditions, "alias=?")
	}
	query := "SELECT token, deviceType, dev FROM registrations WHERE " +
		strings.Join(conditions, " OR ")

	rows, err := db.Query(query, toSliceOfInterface(aliases)...)
	if err != nil {
		return nil, nil, nil, err
	}
	defer rows.Close()

	apnsDev = make([]string, 0)
	apnsProd = make([]string, 0)
	gcmProd = make([]string, 0)
	for rows.Next() {
		var token, deviceType string
		var dev bool
		err := rows.Scan(&token, &deviceType, &dev)
		if err != nil {
			return nil, nil, nil, err
		}
		switch deviceType {
		case "ios":
			if dev {
				apnsDev = append(apnsDev, token)
			} else {
				apnsProd = append(apnsProd, token)
			}
		case "android":
			gcmProd = append(gcmProd, token)
		}
	}
	return
}

func sendAPNS(client apns.APNSClient, token, body string, badge int) chan *apns.PushNotificationResponse {
	payload := apns.NewPayload()
	payload.Alert = body
	payload.Badge = badge

	pn := apns.NewPushNotification()
	pn.DeviceToken = token
	pn.AddPayload(payload)

	// TODO: avoid spawning a goroutine per message
	c := make(chan *apns.PushNotificationResponse)
	go func() {
		c <- client.Send(pn)
	}()

	return c
}
