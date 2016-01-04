package structs

import (
	"time"
)

type User struct {
	Id             string  `json:"id"`
	FirstName      string  `json:"firstName"`
	LastName       string  `json:"lastName"`
	TagId          string  `json:"tagId,omitempty"`
	AvatarPath     string  `json:"avatarPath,omitempty"`
	LastOnlineTime *uint64 `json:"lastOnlineTime,omitempty"`
}

type Bot struct {
	Id      string `json:"id"`
	Name    string `json:"name"`
	GroupId string `json:"groupId"`
}

type Group struct {
	Id          string    `json:"id"`
	CreatedTime time.Time `json:"createdTime"`
	Name        string    `json:"name"`
	IsPublic    bool      `json:"isPublic"`
	Members     []string  `json:"members"`
}

type Message struct {
	Id   int64     `json:"id"`
	Time time.Time `json:"time"`
	Body string    `json:"body"`
	From string    `json:"from"`
	To   string    `json:"to"`
}

type LastReadReceipt struct {
	UserId    string    `json:"userId"`
	MessageId int64     `json:"messageId"`
	Time      time.Time `json:"time"`
}

type GroupMemberChange struct {
	UserId   string    `json:"userId"`
	CallerId string    `json:"callerId"`
	Time     time.Time `json:"time"`
	Added    bool      `json:"added"`
}

//
// FIXME: hack until I can figure out read-only keys
//

type UserWritable struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	TagId     string `json:"tagId"`
}

type BotWritable struct {
	Name    string `json:"name"`
	GroupId string `json:"groupId"`
}

type GroupWritable struct {
	Name     *string  `json:"name"`
	IsPublic *bool    `json:"isPublic"`
	Members  []string `json:"members"`
}

type MessageWritable struct {
	Body string `json:"body"`
}

type LastReadReceiptWritable struct {
	MessageId int64 `json:"messageId"`
}

//
// FIXME: hack because array types seem to be borked
//
// go-restful has problems returning an array of structs.
// Returning a struct containing an array of structs is a workaround.
//

type GroupList struct {
	Groups []Group `json:"groups"`
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

type GroupMemberHistory struct {
	History []GroupMemberChange `json:"history"`
}

//
// These Events are serialized as json and sent over webscocket connections
//

type Event struct {
	// valid resource values:
	// - "USER"
	// - "USER_AVATAR"
	// - "USER_MESSAGE"
	// - "USER_MESSAGE_READ"
	// - "GROUP"
	// - "GROUP_MESSAGE"
	// - "GROUP_MESSAGE_READ"
	// - "LAST_ONLINE"
	// - "BOT"
	// - "TYPING"
	Resource string `json:"resource"`

	// ID of the modified user or group
	Id string `json:"id"`

	// Optional payload for transmitting additional info
	Payload string `json:"payload,omitempty"`
}
