package main

import (
	"aerofs.com/service"
	"aerofs.com/service/mysql"
	"flag"
	"fmt"
	"github.com/emicklei/go-restful"
	"github.com/emicklei/go-restful/swagger"
	"log"
	"net/http"
	"strconv"
	"database/sql"
)

type Consts struct  {

}

type ProblemDetails struct {
	Title  string `json:"title,omitempty" description="A short, human-readable summary of the problem type."`
	Detail string `json:"detail,omitempty" description="An human readable explanation specific to this occurrence of the problem."`
}

func GetDbUrl() (string) {
	config := service.NewConfigClient("identity")
	c, err := config.Get()
	if err != nil {
		panic(err)
	}

	fmt.Println("initializing db")
	return mysql.UrlFromConfig(c)
}

func CreateServer(db *sql.DB, host string, port int, swaggerPath string) *http.Server {
	portStr := ":" + strconv.Itoa(port)
	urlStr := "http://" + host + portStr

	accountResource := AccountsResource{db: db}
	sessionResource := SessionsResource{db: db}
	restful.Add(accountResource.buildEndpoint())
	restful.Add(sessionResource.buildRoutes())

	if len(swaggerPath) > 0 {
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
	return server
}


func main() {
	var useConfig bool
	var port int
	var host string
	var swaggerPath string
	var sharedSecret string
	var dbUrl string

	flag.BoolVar(&useConfig, "cfg", true, "Set false to ignore the config service")
	flag.StringVar(&host, "host", "localhost", "Protocol and host name of the web server")
	flag.IntVar(&port, "port", 80, "Port upon which the web server should listen")
	flag.StringVar(&sharedSecret, "secret", "", "Shared secret to expect from client services")
	flag.StringVar(&swaggerPath, "swagger", "", "Path to swagger-ui files; if missing, swagger will not be invoked.")
	flag.Parse()

	// FIXME: this is ugly; it's here for testing locally
	if useConfig {
		dbUrl = GetDbUrl()
	} else {
		dbUrl = "root@tcp(localhost:3306)/"
	}

	fmt.Printf("Database path %s/%s", dbUrl, "aerofs_sp")

	db := mysql.CreateConnection(dbUrl, "aerofs_sp")
	server := CreateServer(db, host, port, swaggerPath)
	log.Fatal(server.ListenAndServe())
}
