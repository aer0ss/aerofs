package main

import (
	"fmt"
	"flag"
	"github.com/emicklei/go-restful"
	"github.com/emicklei/go-restful/swagger"
	"log"
	"net/http"
	"strconv"
)

type Account struct {
	FirstName          string `json:"first_name,omitempty" description:"User's first name." modelDescription:"foo"`
	LastName           string `json:"last_name,omitempty" description:"User's last name."`
	HasLocalCredential bool   `json:"has_local_credential,omitempty" description:"If true, this account has a local stored credential."`
}

type Session struct {
	created    string `json:"create_date"`
	expiryTime int64  `json:"expiry_time" description:"UNIX epoch time at which this session expires"`
	provenance string `json:"provenance" description:"Indicates the type of identification used to create the session."`
	isAdmin    bool   `json:"is_admin" description:"True for admin-authorized sessions only."`
}

type ProblemDetails struct {
	Title  string `json:"title,omitempty" description="A short, human-readable summary of the problem type."`
	Detail string `json:"detail,omitempty" description="An human readable explanation specific to this occurrence of the problem."`
}

type AccountsResource struct {
	// DAO goes here
}

type SessionsResource struct {
	// DAO goes here
}

func (self *AccountsResource) buildRoutes() *restful.WebService {

	ws := new(restful.WebService)

	ws.Path("/accounts").
		Doc("Create and verify user accounts").
		Consumes(restful.MIME_JSON, restful.MIME_XML).
		Produces(restful.MIME_JSON, restful.MIME_XML)

	ws.Route(ws.GET("/{userid}").To(noop).
		Doc("Examine account details and state.").
		Notes("Return the current state and details of the given account, if it exists.").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(200, "Account exists and is valid.", nil).
		Returns(404, "Account does not exist.", nil))

	ws.Route(ws.HEAD("/{userid}").To(noop).
		Doc("Check account validity.").
		Notes("Return the current state of the given account. Note that HTTP status codes "+
		"are used to indicate the existence	and validity of the requested user account.\n"+
		"\n"+
		"This method provides a simple existence check for clients.").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(200, "Account exists and is valid.", nil).
		Returns(404, "Account does not exist.", nil).
		Returns(410, "Account is disabled.", nil))

	ws.Route(ws.PUT("/{userid}").To(noop).
		Doc("Create or update an account.").
		Notes("If the account does not exist, create a new account record in the system of record.\n" +
		"\n" +
		"Account details that are provided in the request body will be applied to the backing store.\n" +
		"\n" +
		"This endpoint supports the merge-patch described in RFC 7386(https://tools.ietf.org/html/rfc7386). " +
		"In short, a PUT request may include a subset of the Account object; those fields that are provided will " +
		"replace existing values. To delete field contents in the backing store, include the field in the request " +
		"and set it to null. Fields not included will not be altered.").
		Reads(Account{}).
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Do(accountReturns204))

	ws.Route(ws.PUT("/{userid}/credential").To(noop).
		Doc("Replace a stored credential.").
		Notes("The credential is expected in clear text; it will be salted and hashed before storing in the backend.").
		Param(ws.BodyParameter("credential", "plaintext credential").DataType("string")).
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Do(accountReturns204))

	ws.Route(ws.DELETE("/{userid}").To(noop).
		Doc("Delete an account.").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Do(accountReturns204))

	ws.Route(ws.POST("/{userid}/session").To(noop).
		Doc("Create a new session with default lifetime.").
		Notes("Create a session for the given user. Session will be created with the default lifetime").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(201, "Path to newly-created session object", ""))

	return ws
}

func (_ *SessionsResource) buildRoutes() *restful.WebService {
	ws := new(restful.WebService)
	ws.Path("/sessions").
		Doc("Create and verify authenticated sessions.").
		Consumes(restful.MIME_JSON, restful.MIME_XML).
		Produces(restful.MIME_JSON, restful.MIME_XML)

	ws.Route(ws.GET("/{sessionid}").To(noop).
		Doc("Get session details.").
		Notes("Return the current state and details of the given session, if it exists.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns200))

	ws.Route(ws.HEAD("/{sessionid}").To(noop).
		Doc("Get session details.").
		Notes("Return the current state and details of the given session, if it exists.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns200))

	ws.Route(ws.DELETE("/{sessionid}").To(noop).
		Doc("Invalidate the given session.").
		Notes("Mark the given session as invalid immediately.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns204))

	return ws
}

func main() {
	var port int
	var host string
	var swaggerPath string
	var sharedSecret string

	flag.StringVar(&host, "host", "localhost", "Protocol and host name of the web server")
	flag.IntVar(&port, "port", 80, "Port upon which the web server should listen")
	flag.StringVar(&sharedSecret, "secret", "", "Shared secret to expect from client services")
	flag.StringVar(&swaggerPath, "swagger", "", "Path to swagger-ui files; if missing, swagger will not be invoked.")
	flag.Parse()

	portStr := ":" + strconv.Itoa(port)
	urlStr := "http://" + host + portStr

	restful.Add(new(AccountsResource).buildRoutes())
	restful.Add(new(SessionsResource).buildRoutes())

	if (len(swaggerPath) > 0) {
		// The following pulls in the nice Swagger UI.
		// The SwaggerFilePath needs to be the path to the swagger UI as resolved by the web server at runtime.
		// For a deployed server, this might be `/swagger`. For a development machine it might
		// be `src/aerofs.com/identity/swagger-ui/dist`.
		// To use the fancy stuff in swagger, the configured host must match the client's expectations; see CORS.
		config := swagger.Config{
			WebServices:     restful.DefaultContainer.RegisteredWebServices(),
			WebServicesUrl:  urlStr,
			ApiPath:         "/apidocs.json",
			SwaggerPath:     "/apidocs/",
			SwaggerFilePath: swaggerPath}
		swagger.RegisterSwaggerService(config, restful.DefaultContainer)
	}

	log.Print("Identity server now listening on ", urlStr)
	server := &http.Server{Addr: portStr, Handler: restful.DefaultContainer}
	log.Fatal(server.ListenAndServe())
}

func accountReturns204(b *restful.RouteBuilder) {
	b.Returns(http.StatusNoContent, "Account update was successful.", nil)
	b.Returns(http.StatusNotFound, "Account does not exist.", nil)
}

func sessionReturns200(b *restful.RouteBuilder) {
	b.Returns(http.StatusOK, "Session exists and is valid.", nil)
	b.Returns(http.StatusNotFound, "Session does not exist.", nil)
}

func sessionReturns204(b *restful.RouteBuilder) {
	b.Returns(http.StatusNoContent, "Session update was successful.", nil)
	b.Returns(http.StatusNotFound, "Session does not exist.", nil)
}

func noop(req *restful.Request, resp *restful.Response) {
	fmt.Println("Hi mom!", req.SelectedRoutePath())
	resp.WriteHeader(204)
}
