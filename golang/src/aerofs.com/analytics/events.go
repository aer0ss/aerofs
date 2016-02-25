package main

import (
	"errors"
	"time"
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
	Template         Event
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

func lookupEvent(key string) (Event, error) {
	info, ok := eventInfoMap[key]
	if !ok {
		return Event{}, errors.New("Event not found: " + key)
	}
	return info.Template, nil
}

// Translate - translate event names to corresponding objects
var eventInfoMap = map[string]eventInfo{
	"USER_SIGNUP": eventInfo{
		Template: Event{
			Event: "User Sign-Up",
		},
	},
	"ACTIVE_USER": eventInfo{
		Template: Event{
			Event: "Active User",
		},
	},
	"DESKTOP_CLIENT_INSTALL": eventInfo{
		Template: Event{
			Event: "Client Install",
			Properties: map[string]string{
				"Platform": "Desktop",
			},
		},
	},
	"MOBILE_CLIENT_INSTALL": eventInfo{
		Template: Event{
			Event: "Client Install",
			Properties: map[string]string{
				"Platform": "Mobile",
			},
		},
	},
	"LINK_CREATED_DESKTOP": eventInfo{
		Template: Event{
			Event: "Link Created",
			Properties: map[string]string{
				"Source": "Desktop",
			},
		},
		RequireUserID: true,
	},
	"LINK_CREATED_WEB": eventInfo{
		Template: Event{
			Event: "Link Created",
			Properties: map[string]string{
				"Source": "Web",
			},
		},
		RequireUserID: true,
	},
	"FOLDER_INVITATION_SEND": eventInfo{
		Template: Event{
			Event: "Folder Invitation",
			Properties: map[string]string{
				"Action": "Send",
			},
		},
		RequireUserID: true,
	},
	"FOLDER_INVITATION_ACCEPT": eventInfo{
		Template: Event{
			Event: "Folder Invitation",
			Properties: map[string]string{
				"Action": "Accept",
			},
		},
		RequireUserID: true,
	},
	"USER_INVITATION_SEND": eventInfo{
		Template: Event{
			Event: "User Invitation",
			Properties: map[string]string{
				"Action": "Send",
			},
		},
		RequireUserID: true,
	},
	"USER_INVITATION_ACCEPT": eventInfo{
		Template: Event{
			Event: "User Invitation",
			Properties: map[string]string{
				"Action": "Accept",
			},
		},
	},
	"TEAM_SERVER_OFFLINE": eventInfo{
		Template: Event{
			Event: "Team Server Offline",
		},
	},
	"DESKTOP_CLIENT_UNLINK": eventInfo{
		Template: Event{
			Event: "Client Unlink",
			Properties: map[string]string{
				"Platform": "Desktop",
			},
		},
	},
	"MOBILE_CLIENT_UNLINK": eventInfo{
		Template: Event{
			Event: "Client Unlink",
			Properties: map[string]string{
				"Platform": "Mobile",
			},
		},
	},
	"BYTES_SYNCED": eventInfo{
		Template: Event{
			Event: "Bytes Synced",
		},
		AllowValueNotOne: true,
	},
}
