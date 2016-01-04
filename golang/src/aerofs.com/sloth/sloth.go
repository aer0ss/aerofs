package main

import (
	"aerofs.com/service"
	"aerofs.com/service/mysql"
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/resource/bots"
	"aerofs.com/sloth/resource/groups"
	"aerofs.com/sloth/resource/keepalive"
	"aerofs.com/sloth/resource/token"
	"aerofs.com/sloth/resource/users"
	"flag"
	"fmt"
	"github.com/emicklei/go-restful"
	"github.com/emicklei/go-restful/swagger"
	_ "github.com/go-sql-driver/mysql"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"
)

//
// Constants
//

const TOKEN_CACHE_TIME = time.Minute
const TOKEN_CACHE_SIZE = 1000

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

	// format url strings
	portStr := ":" + strconv.Itoa(port)
	restUrlStr := "http://" + host + portStr

	// connect and run migrations
	dbDSN := fmt.Sprintf("root@tcp(%v:3306)/", dbHost)
	dbParams := "charset=utf8mb4"
	db := mysql.CreateConnectionWithParams(dbDSN, dbName, dbParams)
	defer db.Close()

	// create shared variables
	tokenCache := auth.NewTokenCache(TOKEN_CACHE_TIME, TOKEN_CACHE_SIZE)
	lastOnlineTimes := lastOnline.New()
	broadcaster := broadcast.NewBroadcaster()

	// initialize token verifier
	var tokenVerifier auth.TokenVerifier
	if verifier == "echo" {
		tokenVerifier = auth.NewEchoTokenVerifier()
	} else {
		tokenVerifier = auth.NewBifrostTokenVerifier(tokenCache)
	}

	// intitialize shared filters
	checkUserFilter := filters.CheckUser(tokenVerifier)
	updateLastOnlineFilter := filters.UpdateLastOnline(lastOnlineTimes, broadcaster)

	// REST routes
	restful.Add(bots.BuildRoutes(db, broadcaster, checkUserFilter))
	restful.Add(users.BuildRoutes(db, broadcaster, lastOnlineTimes, checkUserFilter, updateLastOnlineFilter))
	restful.Add(groups.BuildRoutes(db, broadcaster, checkUserFilter, updateLastOnlineFilter))
	restful.Add(keepalive.BuildRoutes(checkUserFilter, updateLastOnlineFilter))
	restful.Add(token.BuildRoutes(tokenCache))

	// COOOOOOOORRRRRRRRSSSSSSSSS
	restful.Filter(filters.AddCORSHeaders)
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
