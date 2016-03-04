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

func SendMessageReadEvent(b Broadcaster, cid, uid string, mid int64, members []string) {
	payload := map[string]interface{}{uid: mid}
	multicastEventWithPayload(b, "MESSAGE_READ", cid, payload, members)
}

// Bot profile created,updated
func SendBotEvent(b Broadcaster, bid string) {
	broadcastSimpleEvent(b, "BOT", bid)
}

// Bot avatar updated
func SendBotAvatarEvent(b Broadcaster, bid string) {
	broadcastSimpleEvent(b, "BOT_AVATAR", bid)
}

func SendTypingEvent(b Broadcaster, uid string, cid string, members []string) {
	payload := map[string]interface{}{"cid": cid}
	multicastEventWithPayload(b, "TYPING", uid, payload, members)
}

func SendLastOnlineEvent(b Broadcaster, uid string) {
	broadcastSimpleEvent(b, "LAST_ONLINE", uid)
}

func SendPinEvent(b Broadcaster, uid, cid string) {
	payload := map[string]interface{}{"cid": cid}
	multicastEventWithPayload(b, "PIN", uid, payload, []string{uid})
}

func SendUnpinEvent(b Broadcaster, uid, cid string) {
	payload := map[string]interface{}{"cid": cid}
	multicastEventWithPayload(b, "UNPIN", uid, payload, []string{uid})
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

func multicastEventWithPayload(b Broadcaster, resource, id string, payload map[string]interface{}, targets []string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
		Payload:  payload,
	})
	errors.PanicOnErr(err)
	b.Multicast(bytes, targets)
}
