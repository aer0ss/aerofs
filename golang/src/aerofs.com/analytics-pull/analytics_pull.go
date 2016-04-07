package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"io/ioutil"
	"log"
	"strconv"
	"strings"
	"time"

	"aerofs.com/analytics/db"
	"aerofs.com/analytics/util"
	"aerofs.com/service"
	"aerofs.com/service/config"
	"github.com/boltdb/bolt"
)

// event keys and send interval
const (
	DailyMetricsInterval = time.Hour * 24
	TickerInterval       = time.Second * 10

	auditEnabledKey               = "AUDITING_ENABLED"
	desktopAuthEnabledKey         = "DESKTOP_CLIENT_AUTH_ENABLED"
	mdmEnabledKey                 = "MDM_ENABLED"
	ldapEnabledKey                = "AD/LDAP_ENABLED"
	linkLoginRequiredKey          = "LINK_SIGNIN_REQUIRED_ENABLED"
	groupSyncingEnabledKey        = "LDAP_GROUP_SYNC_ENABLED"
	passwordRestrictionEnabledKey = "PASSWORD_RESTRICTION_ENABLED"
	avgFileCountKey               = "AVERAGE_SHARED_FOLDER_FILE_COUNT"
	maxFileCountKey               = "MAX_SHARED_FOLDER_FILE_COUNT"
	totalFileSizeKey              = "TOTAL_FILE_SIZE"
	macClientKey                  = "MAC_DESKTOP_CLIENTS"
	windowsClientKey              = "WINDOWS_DESKTOP_CLIENTS"
	linuxClientKey                = "LINUX_DESKTOP_CLIENTS"
	groupsKey                     = "GROUPS"
	androidClientKey              = "ANDROID_MOBILE_APPS"
	iosClientKey                  = "IOS_MOBILE_APPS"
	ldapUsersKey                  = "LDAP_USERS"
	localUsersKey                 = "LOCAL_USERS"
	applianceVersionKey           = "APPLIANCE_VERSION"
	sharedFoldersKey              = "SHARED_FOLDERS"
	teamServerCountKey            = "TEAM_SERVER_COUNT"
	seatsAvailableKey             = "SEATS_AVAILABLE"
)

// DailyMetricsKey - key for daily metrics bucket
var DailyMetricsKey = []byte("dailymetrics")
var conf map[string]string
var version string

var clock util.Clock = &util.DefaultClockImpl{}

func addCurrentVersion(metrics map[string][]byte) error {
	metrics[applianceVersionKey] = []byte(version)
	return nil
}

func addSpartaMetrics(metrics map[string][]byte, httpClient ServiceHTTPClient) error {
	statsMap, err := doSpartaStatsRequest("/users", httpClient)
	if err != nil {
		return err
	}
	metrics[localUsersKey] = util.EncodeUint64(statsMap["local"])
	metrics[ldapUsersKey] = util.EncodeUint64(statsMap["ldap"])

	seats, err := strconv.ParseUint(conf["license_seats"], 10, 64)
	if err != nil {
		log.Println("Failed to convert license seats from string:", err)
	} else {
		metrics[seatsAvailableKey] = util.EncodeUint64(seats - statsMap["total"])
	}

	statsMap, err = doSpartaStatsRequest("/devices", httpClient)
	if err != nil {
		return err
	}
	metrics[windowsClientKey] = util.EncodeUint64(statsMap["windows"])
	metrics[macClientKey] = util.EncodeUint64(statsMap["mac"])
	metrics[linuxClientKey] = util.EncodeUint64(statsMap["linux"])
	metrics[iosClientKey] = util.EncodeUint64(statsMap["ios"])
	metrics[androidClientKey] = util.EncodeUint64(statsMap["android"])
	metrics[teamServerCountKey] = util.EncodeUint64(statsMap["team_servers"])

	statsMap, err = doSpartaStatsRequest("/groups", httpClient)
	if err != nil {
		return err
	}
	metrics[groupsKey] = util.EncodeUint64(statsMap["total"])

	statsMap, err = doSpartaStatsRequest("/shares", httpClient)
	if err != nil {
		return err
	}
	metrics[sharedFoldersKey] = util.EncodeUint64(statsMap["total"])

	return nil
}

func doSpartaStatsRequest(route string, httpClient ServiceHTTPClient) (map[string]uint64, error) {
	spartaURL := "http://sparta.service:8085/v1.4/stats"
	req, err := httpClient.NewRequest("GET", spartaURL+route, nil)
	if err != nil {
		return nil, errors.New("Failed to create request to sparta/stats: " + err.Error())
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, errors.New("Failed to retrieve stats from sparta: " + err.Error())
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, errors.New("Received unexpected response code from sparta: " + resp.Status)
	}

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, errors.New("Failed to read stats from response body: " + err.Error())
	}

	statsMap := make(map[string]uint64)
	err = json.Unmarshal(body, &statsMap)
	if err != nil {
		return nil, errors.New("Failed to convert response body json: " + err.Error())
	}
	return statsMap, nil
}

func addConfigMetrics(metrics map[string][]byte) error {
	configIsTrue := func(s string) []byte {
		return util.EncodeBool(strings.EqualFold(s, "true"))
	}

	metrics[auditEnabledKey] = configIsTrue(conf["base.audit.enabled"])
	metrics[desktopAuthEnabledKey] = configIsTrue(conf["device.authorization.endpoint_enabled"])
	metrics[mdmEnabledKey] = configIsTrue(conf["mobile.device.management.enabled"])
	metrics[linkLoginRequiredKey] = configIsTrue(conf["links_require_login.enabled"])
	metrics[groupSyncingEnabledKey] = configIsTrue(conf["ldap.groupsyncing.enabled"])

	metrics[ldapEnabledKey] = util.EncodeBool(strings.EqualFold(conf["lib.authenticator"], "external_credential"))

	// special case for password restriction
	var temp bool
	pref := "password.restriction."
	if pl := conf[pref+"min_password_length"]; pl != "" && pl != "6" {
		temp = true
	} else if nlr := conf[pref+"numbers_letters_required"]; strings.EqualFold("true", nlr) {
		temp = true
	} else if epm := conf[pref+"expiration_period_months"]; epm != "" && epm != "0" {
		temp = true
	} else {
		temp = false
	}
	metrics[passwordRestrictionEnabledKey] = util.EncodeBool(temp)

	return nil
}

type sfStats struct {
	MaxFileCount  uint64 `json:"max_file_count"`
	AvgFileCount  uint64 `json:"avg_file_count"`
	TotalFileSize uint64 `json:"total_file_size"`
}

func addSharedFolderStatsMetrics(metrics map[string][]byte, httpClient ServiceHTTPClient) error {
	polarisURL := "http://polaris.service:8086/stats"
	req, err := httpClient.NewRequest("GET", polarisURL, nil)
	if err != nil {
		return errors.New("Failed to create request to polaris/stats: " + err.Error())
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return errors.New("Failed to retrieve stats from polaris: " + err.Error())
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return errors.New("Received unexpected response code from polaris: " + resp.Status)
	}

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return errors.New("Failed to read stats from response body: " + err.Error())
	}

	var sfStats sfStats
	err = json.Unmarshal(body, &sfStats)
	if err != nil {
		return errors.New("Failed to convert response body json: " + err.Error())
	}
	metrics[avgFileCountKey] = util.EncodeUint64(sfStats.AvgFileCount)
	metrics[maxFileCountKey] = util.EncodeUint64(sfStats.MaxFileCount)
	metrics[totalFileSizeKey] = util.EncodeUint64(sfStats.TotalFileSize / (1000 * 1000))
	return nil
}

func setupDB(db *db.BoltKV) error {
	err := db.Update(func(tx *bolt.Tx) error {
		_, err := tx.CreateBucketIfNotExists(DailyMetricsKey)
		if err != nil {
			return err
		}
		return nil
	})
	if err != nil {
		return errors.New("Failed to setup DB: " + err.Error())
	}

	return nil
}

func getDailyMetrics(db *db.BoltKV, httpClient ServiceHTTPClient) error {
	now := clock.Now().Truncate(DailyMetricsInterval)
	bnow := util.TimeToBytes(now)

	// do nothing if db entry for current period already exists
	exists := false
	err := db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket(DailyMetricsKey).Bucket(bnow)
		if b != nil {
			exists = true
		}
		return nil
	})
	if err != nil {
		return errors.New("Failed to read daily metrics key from db: " + err.Error())
	}
	if exists {
		return nil
	}

	// else fetch metrics from services
	dailyMetrics := make(map[string][]byte)
	// GET DAILY METRICS HERE BY QUERYING SERVICES
	err = addConfigMetrics(dailyMetrics)
	if err != nil {
		return errors.New("Failed to get config metrics: " + err.Error())
	}
	err = addSharedFolderStatsMetrics(dailyMetrics, httpClient)
	if err != nil {
		return errors.New("Failed to get shared folder stats: " + err.Error())
	}
	err = addSpartaMetrics(dailyMetrics, httpClient)
	if err != nil {
		return errors.New("Failed to get stats from sparta: " + err.Error())
	}
	err = addCurrentVersion(dailyMetrics)
	if err != nil {
		return errors.New("Failed to add current version to dailyMetrics: " + err.Error())
	}

	// and persist in db
	err = db.Update(func(tx *bolt.Tx) error {
		dailyMetricsBucket := tx.Bucket(DailyMetricsKey)
		currentBucket, err := dailyMetricsBucket.CreateBucket(bnow)
		if err != nil {
			return errors.New("Failed to create daily metrics sub-bucket: " + err.Error())
		}
		for k, v := range dailyMetrics {
			err = currentBucket.Put([]byte(k), v)
			if err != nil {
				return errors.New("Failed to write key-value pair to db: " + err.Error())
			}
		}
		return nil
	})
	if err != nil {
		return errors.New("Failed to persist daily metrics: " + err.Error())
	}
	return nil
}

func sendToAnalytics(httpClient ServiceHTTPClient, route string, body io.Reader) error {
	req, err := httpClient.NewRequest("POST", "http://analytics.service:9400"+route, body)
	if err != nil {
		return errors.New("Failed to create request to analytics: " + err.Error())
	}

	// blocking send to analytics, wait for analytics to relay to segment
	resp, err := httpClient.Do(req)
	if err != nil {
		return errors.New("Failed to perform request to analytics: " + err.Error())
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return errors.New("Received unexpected response status: " + resp.Status)
	}
	return nil
}

// closure on httpClient used to send a single old bucket to analytics from analytics-pull
func sendDailyMetrics(httpClient ServiceHTTPClient) func(map[string][]byte, time.Time) error {
	return func(dailyMetricsMap map[string][]byte, now time.Time) error {
		b, err := json.Marshal(dailyMetricsMap)
		if err != nil {
			return errors.New("Failed to serialize daily metrics json map: " + err.Error())
		}
		body := bytes.NewBuffer(b)

		err = sendToAnalytics(httpClient, "/dailymetrics", body)

		return nil
	}
}

func teamServerLoop(httpClient ServiceHTTPClient) {
	ticker := time.NewTicker(time.Hour * 2)

	for range ticker.C {
		req, err := httpClient.NewRequest("GET", "http://probe.service:8080/team-servers", bytes.NewBufferString(""))
		if err != nil {
			log.Println("Error creating request to ts-probe:", err)
			continue
		}
		resp, err := httpClient.Do(req)
		if err != nil {
			log.Println("Error getting team server status from ts-probe:", err)
			continue
		}
		resp.Body.Close()

		if resp.StatusCode == 204 {
			continue
			// 503 means team server offline
		} else if resp.StatusCode != 503 {
			log.Println("Received unexpected response status:", resp.Status)
			continue
		}

		event := map[string]interface{}{
			"event": "TEAM_SERVER_OFFLINE",
			"value": 1,
		}
		body, err := json.Marshal(event)
		if err != nil {
			log.Println("Failed to convert team server offline event to json:", err)
			continue
		}

		err = sendToAnalytics(httpClient, "/events", bytes.NewBuffer(body))
		if err != nil {
			log.Println("Failed to send team server offline event to analytics:", err)
			continue
		}
	}
}

func main() {
	log.Println("waiting for deps")
	service.ServiceBarrier()

	enabled := true
	// check if analytics is enabled
	config := config.NewClient("analytics")
	var err error
	conf, err = config.Get()
	if err != nil {
		log.Fatal("Failed to fetch config:", err)
	}
	if !strings.EqualFold(conf["analytics.enabled"], "true") {
		enabled = false
		log.Println("Analytics disabled.")
	}

	versionb, err := ioutil.ReadFile("/opt/repackaging/installers/original/current.ver")
	if err != nil {
		log.Println("Failed to read current.ver file from disk:", err)
	} else {
		split := strings.Split(string(versionb), "=")
		if len(split) == 2 {
			version = strings.TrimSpace(split[1])
		}
		log.Println("Appliance version: '" + version + "'")
	}

	// create or open database file
	db, err := db.NewBoltKV("data/analytics/pulldb", setupDB)
	if err != nil {
		log.Fatal("Failed to open boltdb:", err)
	}
	defer db.Close()

	secret := service.ReadDeploymentSecret()

	// set up daily event sending
	httpClient := NewDefaultServiceHTTPClient("analytics-pull", secret)

	if enabled {
		go teamServerLoop(httpClient)
	}

	ticker := time.NewTicker(TickerInterval)

	// pull if necessary & send old bucket every 10 seconds
	for range ticker.C {
		if enabled {
			// fetch daily metrics and persist
			err := getDailyMetrics(db, httpClient)
			if err != nil {
				log.Println("Failed to get daily metrics:", err)
			}
			// send old buckets
			now := clock.Now()
			err = util.SendBucket(db, DailyMetricsKey, DailyMetricsInterval, now, sendDailyMetrics(httpClient))
			if err != nil {
				log.Println("Failed to send events:", err)
			}
		}
	}
}
