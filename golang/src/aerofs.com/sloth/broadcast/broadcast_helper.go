package broadcast

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"encoding/json"
)

//
// Public
//

func SendUserEvent(b Broadcaster, uid string) {
	broadcastSimpleEvent(b, "USER", uid)
}

func SendUserMessageEvent(b Broadcaster, from string, to string) {
	multicastSimpleEvent(b, "USER_MESSAGE", to, []string{from})
	multicastSimpleEvent(b, "USER_MESSAGE", from, []string{to})
}

func SendUserAvatarEvent(b Broadcaster, uid string) {
	broadcastSimpleEvent(b, "USER_AVATAR", uid)
}

func SendUserMessageReadEvent(b Broadcaster, reader string, other string) {
	multicastSimpleEvent(b, "USER_MESSAGE_READ", other, []string{reader})
	multicastSimpleEvent(b, "USER_MESSAGE_READ", reader, []string{other})
}

func SendGroupEvent(b Broadcaster, gid string, members []string) {
	if members == nil {
		broadcastSimpleEvent(b, "GROUP", gid)
	} else {
		multicastSimpleEvent(b, "GROUP", gid, members)
	}
}

func SendGroupMessageEvent(b Broadcaster, gid string, members []string) {
	multicastSimpleEvent(b, "GROUP_MESSAGE", gid, members)
}

func SendGroupMessageReadEvent(b Broadcaster, gid string, members []string) {
	multicastSimpleEvent(b, "GROUP_MESSAGE_READ", gid, members)
}

func SendBotEvent(b Broadcaster, bid string) {
	broadcastSimpleEvent(b, "BOT", bid)
}

func SendGroupTypingEvent(b Broadcaster, uid string, gid string, members []string) {
	multicastEventWithPayload(b, "TYPING", uid, gid, members)
}

func SendUserTypingEvent(b Broadcaster, uid string, cid string) {
	multicastEventWithPayload(b, "TYPING", uid, uid, []string{cid})
}

func SendLastOnlineEvent(b Broadcaster, uid string) {
	broadcastSimpleEvent(b, "LAST_ONLINE", uid)
}

func SendPinEvent(b Broadcaster, uid, cid string) {
	multicastEventWithPayload(b, "PIN", uid, cid, []string{uid})
}

func SendUnpinEvent(b Broadcaster, uid, cid string) {
	multicastEventWithPayload(b, "UNPIN", uid, cid, []string{uid})
}

//
// Private
//

func broadcastSimpleEvent(b Broadcaster, resource, id string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func multicastSimpleEvent(b Broadcaster, resource, id string, targets []string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
	})
	errors.PanicOnErr(err)
	b.Multicast(bytes, targets)
}

func multicastEventWithPayload(b Broadcaster, resource, id, payload string, targets []string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
		Payload:  payload,
	})
	errors.PanicOnErr(err)
	b.Multicast(bytes, targets)
}
