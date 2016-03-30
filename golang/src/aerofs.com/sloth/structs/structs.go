package structs

import (
	"time"
)

type User struct {
	Id             string  `json:"id"`
	FirstName      string  `json:"firstName"`
	LastName       string  `json:"lastName"`
	TagId          string  `json:"tagId,omitempty"`
	Phone          string  `json:"phone,omitempty"`
	AvatarPath     string  `json:"avatarPath,omitempty"`
	LastOnlineTime *uint64 `json:"lastOnlineTime,omitempty"`
}

type Bot struct {
	Id          string    `json:"id"`
	Name        string    `json:"name"`
	ConvoId     string    `json:"convoId"`
	AvatarPath  string    `json:"avatarPath,omitempty"`
	CreatorId   string    `json:"creatorId"`
	CreatedTime time.Time `json:"createdTime"`
}

type Convo struct {
	Id          string           `json:"id"`
	Type        string           `json:"type"`
	IsPinned    bool             `json:"isPinned"`
	Receipts    map[string]int64 `json:"receipts,omitempty"`
	Sid         string           `json:"sid,omitempty"`
	CreatedTime time.Time        `json:"createdTime"`
	Name        string           `json:"name,omitempty"`
	IsPublic    bool             `json:"isPublic"`
	Members     []string         `json:"members"`
	Bots        []string         `json:"bots"`
}

type Message struct {
	Id     int64     `json:"id"`
	Time   time.Time `json:"time"`
	Body   string    `json:"body"`
	From   string    `json:"from"`
	To     string    `json:"to"`
	IsData bool      `json:"isData,omitempty"`
}

type LastReadReceipt struct {
	UserId    string    `json:"userId"`
	MessageId int64     `json:"messageId"`
	Time      time.Time `json:"time"`
}

type Command struct {
	Command     string `json:"command"`
	Method      string `json:"method"`
	URL         string `json:"url"`
	Token       string `json:"token"`
	Syntax      string `json:"syntax"`
	Description string `json:"description"`
}

type CommandResponse struct {
	Text string `json:"text"`
}

type UserSettings struct {
	NotifyOnlyOnTag *bool `json:"notifyOnlyOnTag"`
}

//
// FIXME: hack until I can figure out read-only keys
//

type UserWritable struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	TagId     string `json:"tagId"`
	Phone     string `json:"phone"`
}

type BotWritable struct {
	Name      string `json:"name"`
	ConvoId   string `json:"convoId"`
	CreatorId string `json:"creatorId"`
}

type DirectConvoWritable struct {
	Members []string `json:"members"`
}

type GroupConvoWritable struct {
	Members  []string `json:"members"`
	Name     *string  `json:"name"`
	IsPublic *bool    `json:"isPublic"`
}

type FileConvoWritable struct {
	FileId   string `json:"fileId"`
	Name     string `json:"name"`
	RootSid  string `json:"rootFolderId"`
	IsPublic bool   `json:"isPublic"`
}

type MessageWritable struct {
	Body string `json:"body"`
}

type LastReadReceiptWritable struct {
	MessageId int64 `json:"messageId"`
}

type CommandWritable struct {
	Command     string `json:"command"`
	Method      string `json:"method"`
	URL         string `json:"url"`
	Token       string `json:"token"`
	Syntax      string `json:"syntax"`
	Description string `json:"description"`
}

//
// FIXME: hack because array types seem to be borked
//
// go-restful has problems returning an array of structs.
// Returning a struct containing an array of structs is a workaround.
//

type ConvoList struct {
	Convos map[string]Convo `json:"convos"`
}

type UserList struct {
	Users []User `json:"users"`
}

type BotList struct {
	Bots []Bot `json:"bots"`
}

type MessageList struct {
	Messages []Message `json:"messages"`
}

type IdList struct {
	Ids []string `json:"ids"`
}

type LastReadReceiptList struct {
	LastRead []LastReadReceipt `json:"lastRead"`
}

type CommandList struct {
	Commands []Command `json:"commands"`
}

//
// These Events are serialized as json and sent over webscocket connections
//

type Event struct {
	// valid resource values:
	// - "USER"
	// - "USER_AVATAR"
	// - "CONVO"
	// - "MESSAGE"
	// - "LAST_ONLINE"
	// - "BOT"
	// - "TYPING"
	Resource string `json:"resource"`

	// ID of the modified user or convo
	Id string `json:"id"`

	// Optional payload for transmitting additional info
	Payload map[string]interface{} `json:"payload,omitempty"`
}
