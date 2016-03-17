package main

import (
	"errors"
	"time"

	"aerofs.com/analytics/segment"
)

// Event - represents an analytics event
type Event struct {
	Event      string            `json:"event"`
	CustomerID string            `json:"customer_id,omitempty"`
	Timestamp  *time.Time        `json:"timestamp,omitempty"`
	Properties map[string]string `json:"properties,omitempty"`
	UserID     string            `json:"user_id,omitempty"`
	Value      uint64            `json:"value,omitempty"`
}

type eventInfo struct {
	Template         segment.Track
	AllowValueNotOne bool
	RequireUserID    bool
}

func validateEvent(event *Event) error {
	info, ok := eventInfoMap[event.Event]
	if !ok {
		return errors.New("Event not found: " + event.Event)
	}
	if !(info.AllowValueNotOne || event.Value == 1) {
		return errors.New("Event value must be equal to 1 for: " + event.Event)
	}
	if info.RequireUserID && event.UserID == "" {
		return errors.New("Event user_id must be present for: " + event.Event)
	}
	if !info.RequireUserID && event.UserID != "" {
		return errors.New("Event user_id must not be present for: " + event.Event)
	}

	return nil
}

func lookupEvent(key string) (segment.Track, error) {
	info, ok := eventInfoMap[key]
	if !ok {
		return segment.Track{}, errors.New("Event not found: " + key)
	}
	return info.Template, nil
}

// Translate - translate event names to corresponding objects
var eventInfoMap = map[string]eventInfo{
	"USER_SIGNUP": eventInfo{
		Template: segment.Track{
			Event:      "User Sign-Up",
			Properties: make(map[string]interface{}),
		},
	},
	"ACTIVE_USER": eventInfo{
		Template: segment.Track{
			Event:      "Active User",
			Properties: make(map[string]interface{}),
		},
	},
	"DESKTOP_CLIENT_INSTALL": eventInfo{
		Template: segment.Track{
			Event: "Client Install",
			Properties: map[string]interface{}{
				"Platform": "Desktop",
			},
		},
	},
	"MOBILE_CLIENT_INSTALL": eventInfo{
		Template: segment.Track{
			Event: "Client Install",
			Properties: map[string]interface{}{
				"Platform": "Mobile",
			},
		},
	},
	"LINK_CREATED_DESKTOP": eventInfo{
		Template: segment.Track{
			Event: "Link Created",
			Properties: map[string]interface{}{
				"Source": "Desktop",
			},
		},
		RequireUserID: true,
	},
	"LINK_CREATED_WEB": eventInfo{
		Template: segment.Track{
			Event: "Link Created",
			Properties: map[string]interface{}{
				"Source": "Web",
			},
		},
		RequireUserID: true,
	},
	"FOLDER_INVITATION_SEND": eventInfo{
		Template: segment.Track{
			Event: "Folder Invitation",
			Properties: map[string]interface{}{
				"Action": "Send",
			},
		},
		RequireUserID: true,
	},
	"FOLDER_INVITATION_ACCEPT": eventInfo{
		Template: segment.Track{
			Event: "Folder Invitation",
			Properties: map[string]interface{}{
				"Action": "Accept",
			},
		},
		RequireUserID: true,
	},
	"USER_INVITATION_SEND": eventInfo{
		Template: segment.Track{
			Event: "User Invitation",
			Properties: map[string]interface{}{
				"Action": "Send",
			},
		},
		RequireUserID: true,
	},
	"USER_INVITATION_ACCEPT": eventInfo{
		Template: segment.Track{
			Event: "User Invitation",
			Properties: map[string]interface{}{
				"Action": "Accept",
			},
		},
	},
	"TEAM_SERVER_OFFLINE": eventInfo{
		Template: segment.Track{
			Event:      "Team Server Offline",
			Properties: make(map[string]interface{}),
		},
	},
	"DESKTOP_CLIENT_UNLINK": eventInfo{
		Template: segment.Track{
			Event: "Client Unlink",
			Properties: map[string]interface{}{
				"Platform": "Desktop",
			},
		},
	},
	"MOBILE_CLIENT_UNLINK": eventInfo{
		Template: segment.Track{
			Event: "Client Unlink",
			Properties: map[string]interface{}{
				"Platform": "Mobile",
			},
		},
	},
	"BYTES_SYNCED": eventInfo{
		Template: segment.Track{
			Event:      "Bytes Synced",
			Properties: make(map[string]interface{}),
		},
		AllowValueNotOne: true,
	},
}
