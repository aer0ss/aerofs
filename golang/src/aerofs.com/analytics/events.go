package main

import (
	"errors"
	"time"

	"aerofs.com/analytics/segment"
)

// constants to represent type of value field
const (
	Integer = 0
	Boolean = 1
	String  = 2
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
	Template      segment.Track
	ValueType     int
	RequireUserID bool
}

func validateEvent(event *Event) error {
	info, ok := eventInfoMap[event.Event]
	if !ok {
		return errors.New("Event not found: " + event.Event)
	}
	if info.RequireUserID && event.UserID == "" {
		return errors.New("Event user_id must be present for: " + event.Event)
	}
	if !info.RequireUserID && event.UserID != "" {
		return errors.New("Event user_id must not be present for: " + event.Event)
	}
	if event.Value != 1 {
		return errors.New("Event value must be 1.")
	}

	return nil
}

func lookupDailyMetric(key string) (eventInfo, error) {
	info, ok := dailyMetricInfoMap[key]
	if !ok {
		return eventInfo{}, errors.New("Daily metric not found: " + key)
	}
	return info, nil
}

func lookupEvent(key string) (eventInfo, error) {
	info, ok := eventInfoMap[key]
	if !ok {
		return eventInfo{}, errors.New("Event not found: " + key)
	}
	return info, nil
}

var dailyMetricInfoMap = map[string]eventInfo{
	"SEATS_AVAILABLE": eventInfo{
		Template: segment.Track{
			Event:      "Seats Available",
			Properties: make(map[string]interface{}),
		},
	},
	"AVERAGE_SHARED_FOLDER_FILE_COUNT": eventInfo{
		Template: segment.Track{
			Event:      "Average Shared Folder File Count",
			Properties: make(map[string]interface{}),
		},
	},
	"MAX_SHARED_FOLDER_FILE_COUNT": eventInfo{
		Template: segment.Track{
			Event:      "Max Shared Folder File Count",
			Properties: make(map[string]interface{}),
		},
	},
	"TOTAL_FILE_SIZE": eventInfo{
		Template: segment.Track{
			Event:      "Total File Size (MB)",
			Properties: make(map[string]interface{}),
		},
	},
	"MAC_DESKTOP_CLIENTS": eventInfo{
		Template: segment.Track{
			Event: "Desktop Client Count",
			Properties: map[string]interface{}{
				"OS": "Mac",
			},
		},
	},
	"WINDOWS_DESKTOP_CLIENTS": eventInfo{
		Template: segment.Track{
			Event: "Desktop Client Count",
			Properties: map[string]interface{}{
				"OS": "Windows",
			},
		},
	},
	"LINUX_DESKTOP_CLIENTS": eventInfo{
		Template: segment.Track{
			Event: "Desktop Client Count",
			Properties: map[string]interface{}{
				"OS": "Linux",
			},
		},
	},
	"GROUPS": eventInfo{
		Template: segment.Track{
			Event:      "Group Count",
			Properties: make(map[string]interface{}),
		},
	},
	"ANDROID_MOBILE_APPS": eventInfo{
		Template: segment.Track{
			Event: "Mobile Client Count",
			Properties: map[string]interface{}{
				"OS": "Android",
			},
		},
	},
	"IOS_MOBILE_APPS": eventInfo{
		Template: segment.Track{
			Event: "Mobile Client Count",
			Properties: map[string]interface{}{
				"OS": "iOS",
			},
		},
	},
	"LDAP_USERS": eventInfo{
		Template: segment.Track{
			Event: "User Count",
			Properties: map[string]interface{}{
				"Account Type": "AD/LDAP",
			},
		},
	},
	"LOCAL_USERS": eventInfo{
		Template: segment.Track{
			Event: "User Count",
			Properties: map[string]interface{}{
				"Account Type": "Local",
			},
		},
	},
	"APPLIANCE_VERSION": eventInfo{
		Template: segment.Track{
			Event:      "Appliance Version",
			Properties: make(map[string]interface{}),
		},
		ValueType: String,
	},
	"SHARED_FOLDERS": eventInfo{
		Template: segment.Track{
			Event:      "Shared Folder Count",
			Properties: make(map[string]interface{}),
		},
	},
	"AUDITING_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "Auditing",
			},
		},
		ValueType: Boolean,
	},
	"DESKTOP_CLIENT_AUTH_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "Desktop Client Authorization",
			},
		},
		ValueType: Boolean,
	},
	"MDM_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "MDM",
			},
		},
		ValueType: Boolean,
	},
	"AD/LDAP_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "AD/LDAP",
			},
		},
		ValueType: Boolean,
	},
	"LINK_SIGNIN_REQUIRED_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "Link Sign-in Required",
			},
		},
		ValueType: Boolean,
	},
	"PASSWORD_RESTRICTION_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "Password Restriction",
			},
		},
		ValueType: Boolean,
	},
	"LDAP_GROUP_SYNC_ENABLED": eventInfo{
		Template: segment.Track{
			Event: "Feature Enabled",
			Properties: map[string]interface{}{
				"Feature": "LDAP Group Syncing",
			},
		},
		ValueType: Boolean,
	},
	"TEAM_SERVER_COUNT": eventInfo{
		Template: segment.Track{
			Event:      "Team Server Count",
			Properties: make(map[string]interface{}),
		},
	},
}

// Translate - translate event names to corresponding objects
var eventInfoMap = map[string]eventInfo{
	"SETUP_COMPLETE": eventInfo{
		Template: segment.Track{
			Event:      "Setup Complete",
			Properties: make(map[string]interface{}),
		},
	},
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
		RequireUserID: true,
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
	"LINK_CREATED": eventInfo{
		Template: segment.Track{
			Event:      "Link Created",
			Properties: make(map[string]interface{}),
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
	"USER_DELETE": eventInfo{
		Template: segment.Track{
			Event:      "User Delete",
			Properties: make(map[string]interface{}),
		},
	},
	"SHARED_FOLDER_CREATE": eventInfo{
		Template: segment.Track{
			Event:      "Shared Folder Creation",
			Properties: make(map[string]interface{}),
		},
		RequireUserID: true,
	},
}
