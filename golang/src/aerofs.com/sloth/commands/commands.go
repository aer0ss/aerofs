// Package commands is used to facilitate the parsing and handling of messages
// that contain a slash command
package commands

import (
	"aerofs.com/sloth/dao"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
)

type Handler struct {
	db *sql.DB
}

func NewHandler(db *sql.DB) *Handler {
	return &Handler{db: db}
}

// A message is a slash command if:
//  - prefixed with '/', AND
//  - the text command exists in the commands table
func (h *Handler) IsSlashCommand(msg string) bool {
	if msg[0] != '/' {
		return false
	}

	cmd, _ := parseMessage(msg)
	tx := dao.BeginOrPanic(h.db)
	_, err := dao.GetCommand(tx, cmd)
	if err != nil {
		tx.Rollback()
		return false
	}

	dao.CommitOrPanic(tx)
	return true
}

// Given a slash command, return the command, message, error
func (h *Handler) HandleCommand(from, to, body string) (string, string, error) {
	// Check command existence
	bodyCmd, bodyText := parseMessage(body)
	tx := dao.BeginOrPanic(h.db)
	cmd, err := dao.GetCommand(tx, bodyCmd)
	if err != nil {
		tx.Rollback()
		return bodyCmd, "", errors.New("Command does not exist")
	}

	dao.CommitOrPanic(tx)

	// Construct request
	// TODO : Should we serialize more values similar to Slack and Hugues' work?
	data := url.Values{
		"token":      []string{cmd.Token},
		"command":    []string{cmd.Command},
		"text":       []string{bodyText},
		"user_id":    []string{from},
		"channel_id": []string{to},
	}.Encode()

	endpoint := cmd.URL
	var reqBody io.Reader

	switch cmd.Method {
	case "GET":
		endpoint += "?" + data
	case "POST":
		reqBody = strings.NewReader(data)
	}

	request, err := http.NewRequest(cmd.Method, endpoint, reqBody)
	if err != nil {
		return bodyCmd, "", err
	}
	if reqBody != nil {
		request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	}

	// Perform request
	client := new(http.Client)
	resp, err := client.Do(request)
	if err != nil {
		return bodyCmd, "", err
	}

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusBadRequest {
		return bodyCmd, "", errors.New("Unable to retrieve command response")
	}

	// Demarshal
	var respMsg CommandResponse
	respBody, err := ioutil.ReadAll(resp.Body)
	defer resp.Body.Close()
	err = json.Unmarshal(respBody, &respMsg)
	if err != nil {
		return bodyCmd, "", errors.New("Unable to unmarshal command response")
	}

	return cmd.Command, respMsg.Text, nil
}

//
// private
//

// Parse a message for a command and its arguments
func parseMessage(msg string) (string, string) {
	i := strings.IndexByte(msg, ' ')
	if i == -1 {
		i = len(msg)
	}
	cmd := msg[1:i]
	cmd_args := strings.TrimSpace(msg[i:])
	return cmd, cmd_args
}
