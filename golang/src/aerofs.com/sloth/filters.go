package main

import (
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/errors"
	"encoding/json"
	"github.com/emicklei/go-restful"
	"log"
	"strings"
	"time"
)

// log request, caller, and path params
func LogRequest(request *restful.Request, response *restful.Response, chain *restful.FilterChain) {
	log.Printf("%v %v %v (%v)\n",
		request.Request.Method,
		request.SelectedRoutePath(),
		request.PathParameters(),
		request.Attribute(AuthorizedUser),
	)
	chain.ProcessFilter(request, response)
}

// Add CORS headers
func AddCORSHeaders(request *restful.Request, response *restful.Response, chain *restful.FilterChain) {
	response.AddHeader(restful.HEADER_AccessControlAllowOrigin, "*")
	response.AddHeader(restful.HEADER_AccessControlAllowHeaders,
		"Authorization, Cache-Control, Content-Type")
	response.AddHeader(restful.HEADER_AccessControlAllowMethods,
		"GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD")
	chain.ProcessFilter(request, response)
}

// Checks the Authorization (and query params for GET requests) for a Bearer
// token, determines the owner, and sets the Authorized-User attribute to the
// owner's UID.  Returns a 403 if the token is not present or not valid.
//
// Expects "Authorization: Bearer <token>"
//		OR "query param: authorization=<token>" (GET only)
func CheckUser(request *restful.Request, response *restful.Response, chain *restful.FilterChain) {
	// get token from cookie or Authorization header
	// return 401 if neither is present
	token := getTokenFromAuthHeader(request)
	if token == "" && request.Request.Method == "GET" {
		token = getTokenFromQueryParam(request)
	}
	if token == "" {
		response.WriteErrorString(401, "Invalid authorization token")
		return
	}
	// verify token's validity
	owner, err := tokenVerifier.VerifyToken(token)
	_, ok := err.(*auth.TokenNotFoundError)
	if ok {
		response.WriteErrorString(401, "Invalid authorization token")
		return
	}
	errors.PanicOnErr(err)
	// set the Authorized-User attribute
	request.SetAttribute(AuthorizedUser, owner)
	chain.ProcessFilter(request, response)
}

// Returns 403 if the target uid does not match the Authorized-User attribute
func UserIsTarget(request *restful.Request, response *restful.Response, chain *restful.FilterChain) {
	target := request.PathParameter("uid")
	owner := request.Attribute(AuthorizedUser)
	if owner == nil {
		response.WriteHeader(500)
		return
	}
	if target != owner {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	chain.ProcessFilter(request, response)
}

// Keep track of the last time each user's presence was broadcasted.
// Broadcast at most once per BROADCAST_TIMEOUT_SECONDS (defined in sloth.go)
var lastBroadcastTimes = make(map[string]time.Time)

// Keep track of the last time each user interacted with the server
func UpdateLastOnline(request *restful.Request, response *restful.Response, chain *restful.FilterChain) {
	caller := request.Attribute(AuthorizedUser).(string)
	lastOnlineTimesMutex.Lock()
	now := time.Now()
	lastOnlineTimes[caller] = now
	lastBroadcastTime, ok := lastBroadcastTimes[caller]
	if !ok || now.Sub(lastBroadcastTime).Seconds() > BROADCAST_TIMEOUT_SECONDS {
		lastBroadcastTimes[caller] = now
		lastOnlineTimesMutex.Unlock()
		broadcastUpdateLastOnline(caller)
	} else {
		lastOnlineTimesMutex.Unlock()
	}
	chain.ProcessFilter(request, response)
}

//
// Helpers
//

// Returns the auth token, or empty-string if no token is given
func getTokenFromAuthHeader(request *restful.Request) string {
	authHeader := request.HeaderParameter("Authorization")
	if !strings.HasPrefix(authHeader, "Bearer ") {
		return ""
	}
	return strings.TrimPrefix(authHeader, "Bearer ")
}

// Returns the token provided by the authorization query param, or empty-string
// if no token is given
func getTokenFromQueryParam(request *restful.Request) string {
	return request.QueryParameter("authorization")
}

// Relies on a global "broadcaster" var, because filters don't take args
func broadcastUpdateLastOnline(uid string) {
	log.Println("broadcast online", uid)
	bytes, err := json.Marshal(Event{
		Resource: "LAST_ONLINE",
		Id:       uid,
	})
	errors.PanicOnErr(err)
	broadcaster.Broadcast(bytes)
}
