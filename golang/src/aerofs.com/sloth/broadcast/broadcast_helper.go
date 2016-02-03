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

func SendUserAvatarEvent(b Broadcaster, uid string) {
	broadcastSimpleEvent(b, "USER_AVATAR", uid)
}

func SendConvoEvent(b Broadcaster, cid string, members []string) {
	if members == nil {
		broadcastSimpleEvent(b, "CONVO", cid)
	} else {
		multicastSimpleEvent(b, "CONVO", cid, members)
	}
}

func SendMessageEvent(b Broadcaster, cid string, members []string) {
	multicastSimpleEvent(b, "MESSAGE", cid, members)
}

func SendMessageReadEvent(b Broadcaster, cid string, members []string) {
	multicastSimpleEvent(b, "MESSAGE_READ", cid, members)
}

func SendBotEvent(b Broadcaster, bid string) {
	broadcastSimpleEvent(b, "BOT", bid)
}

func SendTypingEvent(b Broadcaster, uid string, cid string, members []string) {
	multicastEventWithPayload(b, "TYPING", uid, cid, members)
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
