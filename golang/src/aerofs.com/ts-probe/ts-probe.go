package main

import (
    "fmt"
    "time"
    "errors"
    "net/http"
    "io/ioutil"
    "encoding/json"
    "aerofs.com/service"
)

var SECRET string = service.ReadDeploymentSecret()
var AUTH_HEADER string = "Aero-Service-Shared-Secret probe " + SECRET

type Device struct {
    Id          string    `json:"id"`
    Owner       string    `json:"owner"`
    Name        string    `json:"name"`
    OsFamily    string    `json:"os_family"`
    InstallDate time.Time `json:"install_date"`
}

type DeviceStatus struct {
    Online   bool      `json:"online"`
    LastSeen time.Time `json:"last_seen"`
}

func spartaGET(client *http.Client, route string, v interface{}) error {
    req, err := http.NewRequest("GET", "http://sparta.service:8085/v1.3" + route, nil)
    if err != nil { return err }
    req.Header.Add("Authorization", AUTH_HEADER)
    resp, err := client.Do(req)
    if err != nil { return err }
    if resp.StatusCode != 200 {
        return errors.New("unexpected status code: " + resp.Status)
    }
    b, err := ioutil.ReadAll(resp.Body)
    if err != nil { return err }
    return json.Unmarshal(b, v)
}

func listTeamServers(client *http.Client) ([]Device, error) {
    var devices []Device
    return devices, spartaGET(client, "/users/:2/devices", &devices)
}

func deviceStatus(did string, client *http.Client) (DeviceStatus, error) {
    var status DeviceStatus
    return status, spartaGET(client, "/devices/" + did + "/status", &status)
}

func probe() (int, string) {
    client := &http.Client{}
    devices, err := listTeamServers(client)
    if err != nil {
        return http.StatusServiceUnavailable, "failed to list TS: " + err.Error()
    }
    for _,device := range devices {
        fmt.Println("device " + device.Id + " " + device.Name)
        if time.Since(device.InstallDate).Seconds() < 300 {
            // ignore freshly installed device
            fmt.Println("ignore freshly installed")
            continue
        }
        status, err := deviceStatus(device.Id, client)
        if err != nil {
            return http.StatusServiceUnavailable, "failed to get status: " + err.Error()
        }
        if !status.Online {
            return http.StatusServiceUnavailable, "device offline: " + status.LastSeen.String()
        }
    }
    return http.StatusNoContent, "ok"
}

func probeHandler(w http.ResponseWriter, r *http.Request) {
    fmt.Println(r.Method + " " + r.RequestURI)
    code, msg := probe()
    fmt.Println(" > " + msg)
    w.Header().Set("Content-Length", "0")
    w.WriteHeader(code)
}

func main() {
    service.ServiceBarrier()

    http.HandleFunc("/", probeHandler)
    fmt.Println("Probe serving at 8080")
    err := http.ListenAndServe(":8080", nil)
    if err != nil { panic("failed: " + err.Error()) }
}

