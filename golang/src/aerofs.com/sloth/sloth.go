package main

import (
	"aerofs.com/service"
	"aerofs.com/service/config"
	"aerofs.com/service/mysql"
	"aerofs.com/sloth/aeroclients/lipwig"
	"aerofs.com/sloth/aeroclients/polaris"
	"aerofs.com/sloth/aeroclients/sparta"
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/push"
	"aerofs.com/sloth/resource/bots"
	"aerofs.com/sloth/resource/commands"
	"aerofs.com/sloth/resource/convos"
	"aerofs.com/sloth/resource/keepalive"
	pushResource "aerofs.com/sloth/resource/push"
	"aerofs.com/sloth/resource/token"
	"aerofs.com/sloth/resource/users"
	"crypto/tls"
	"crypto/x509"
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
const HTTP_CLIENT_POOL_SIZE = 20
const MAX_OPEN_DB_CONNS = 30

//
// Lipwig Client Config
//

func getTlsConfig() *tls.Config {
	cfg, err := config.NewClient("sloth").Get()
	errors.PanicOnErr(err)
	rawCacert, ok := cfg["config.loader.base_ca_certificate"]
	if !ok {
		panic("missing ca cert config")
	}
	hostname, ok := cfg["base.host.unified"]
	if !ok {
		panic("missing hostname config")
	}
	certPool := x509.NewCertPool()
	if !certPool.AppendCertsFromPEM([]byte(rawCacert)) {
		panic("unable to parse CA cert")
	}
	return &tls.Config{
		ServerName: hostname,
		RootCAs:    certPool,
	}
}

//
// Main
//

func main() {
	var port int
	var host, dbHost, dbName string
	var swaggerFilePath string
	var verifier, deploymentSecret string
	var pushEnabled, fileUpdatesEnabled bool

	// wait for dependent containers
	service.ServiceBarrier()

	// parse command-line args
	flag.StringVar(&host, "host", "localhost", "Host name of the web server")
	flag.IntVar(&port, "port", 8001, "Port upon which the server should listen")
	flag.StringVar(&dbName, "db", "sloth", "MySQL database to use")
	flag.StringVar(&dbHost, "dbHost", "localhost", "MySQL host address")
	flag.StringVar(&swaggerFilePath, "swagger", os.Getenv("HOME")+"/repos/swagger-ui/dist", "Path to swagger-ui files; if missing, swagger will not be invoked.")
	flag.StringVar(&verifier, "verifier", "bifrost", "Token verifier to use. Currently \"bifrost\" or \"echo\"")
	flag.BoolVar(&pushEnabled, "pushEnabled", true, "Set false to disable push notifications")
	flag.BoolVar(&fileUpdatesEnabled, "fileUpdatesEnabled", true, "Set false to disable file update subscriptions")
	flag.StringVar(&deploymentSecret, "deploymentSecret", "", "Set deployment secret instead of reading from disk")
	flag.Parse()

	// read deployment secret from disk if not provided by command-line arg
	if deploymentSecret == "" {
		deploymentSecret = service.ReadDeploymentSecret()
	}

	// format url strings
	portStr := ":" + strconv.Itoa(port)
	restUrlStr := "http://" + host + portStr

	// connect to db and run migrations
	dbDSN := fmt.Sprintf("root@tcp(%v:3306)/", dbHost)
	dbParams := "charset=utf8mb4"
	db := mysql.CreateConnectionWithParams(dbDSN, dbName, dbParams)
	db.SetMaxOpenConns(MAX_OPEN_DB_CONNS)
	db.SetMaxIdleConns(0)
	defer db.Close()

	var pushNotifier push.Notifier
	if pushEnabled {
		// connect to config
		cfg, err := config.NewClient("sloth").Get()
		errors.PanicOnErr(err)

		// grab button config and initialize push notifier
		buttonAuthUser, userOk := cfg["messaging.button.auth.user"]
		buttonAuthPass, passOk := cfg["messaging.button.auth.pass"]
		buttonBaseUrl, urlOk := cfg["messaging.button.url.base"]
		if !userOk || !passOk || !urlOk {
			panic("missing button config")
		}
		pushNotifier = push.NewNotifier(buttonAuthUser, buttonAuthPass, buttonBaseUrl, HTTP_CLIENT_POOL_SIZE)
	} else {
		pushNotifier = push.NewNoopNotifier()
	}

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

	// create http clients for aero services
	polarisClient := polaris.NewClient(deploymentSecret)
	spartaClient := sparta.NewClient(deploymentSecret)

	// subscribe to SSMP - polaris
	var lipwigClient *lipwig.Client
	if fileUpdatesEnabled {
		tlsConfig := getTlsConfig()
		lipwigClient = lipwig.Start(tlsConfig, db, deploymentSecret, polarisClient, spartaClient)
	}

	// intitialize shared filters
	checkUserFilter := filters.CheckUser(tokenVerifier)
	updateLastOnlineFilter := filters.UpdateLastOnline(lastOnlineTimes, broadcaster)

	// REST routes
	restful.Add(bots.BuildRoutes(
		db,
		broadcaster,
		checkUserFilter,
	))
	restful.Add(users.BuildRoutes(
		db,
		broadcaster,
		lastOnlineTimes,
		pushNotifier,
		checkUserFilter,
		updateLastOnlineFilter,
		spartaClient,
	))
	restful.Add(convos.BuildRoutes(
		db,
		broadcaster,
		lastOnlineTimes,
		pushNotifier,
		checkUserFilter,
		updateLastOnlineFilter,
		spartaClient,
		lipwigClient,
	))
	restful.Add(keepalive.BuildRoutes(
		checkUserFilter,
		updateLastOnlineFilter,
	))
	restful.Add(token.BuildRoutes(
		tokenCache,
	))
	restful.Add(pushResource.BuildRoutes(
		checkUserFilter,
		updateLastOnlineFilter,
		pushNotifier,
	))
	restful.Add(commands.BuildRoutes(
		db,
		checkUserFilter,
	))

	// COOOOOOOORRRRRRRRSSSSSSSSS
	restful.Filter(filters.AddCORSHeaders)
	restful.Filter(restful.OPTIONSFilter())

	// Swagger API doc config
	swaggerCfg := swagger.Config{
		WebServices:     restful.DefaultContainer.RegisteredWebServices(),
		WebServicesUrl:  restUrlStr,
		ApiPath:         "/apidocs.json",
		SwaggerPath:     "/apidocs/",
		SwaggerFilePath: swaggerFilePath,
	}
	swagger.RegisterSwaggerService(swaggerCfg, restful.DefaultContainer)

	// listen for REST connections
	log.Print("REST server listening on ", restUrlStr)
	server := &http.Server{
		Addr:    portStr,
		Handler: NewMultiplexingHandler(tokenVerifier, broadcaster, restful.DefaultContainer),
	}
	log.Fatal(server.ListenAndServe())
}
