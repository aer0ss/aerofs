package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"encoding/json"
)

//
// Utility Functions
//

func _broadcastSimpleEvent(b broadcast.Broadcaster, resource, id string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func _multicastSimpleEvent(b broadcast.Broadcaster, resource, id string, targets []string) {
	bytes, err := json.Marshal(Event{
		Resource: resource,
		Id:       id,
	})
	errors.PanicOnErr(err)
	b.Multicast(bytes, targets)
}

//
// Multicast/Broadcast functions
//

func sendUserEvent(b broadcast.Broadcaster, uid string) {
	_broadcastSimpleEvent(b, "USER", uid)
}

func sendUserMessageEvent(b broadcast.Broadcaster, from string, to string) {
	_multicastSimpleEvent(b, "USER_MESSAGE", to, []string{from})
	_multicastSimpleEvent(b, "USER_MESSAGE", from, []string{to})
}

func sendUserAvatarEvent(b broadcast.Broadcaster, uid string) {
	_broadcastSimpleEvent(b, "USER_AVATAR", uid)
}

func sendUserMessageReadEvent(b broadcast.Broadcaster, reader string, other string) {
	_multicastSimpleEvent(b, "USER_MESSAGE_READ", other, []string{reader})
	_multicastSimpleEvent(b, "USER_MESSAGE_READ", reader, []string{other})
}

func sendGroupEvent(b broadcast.Broadcaster, gid string, members []string) {
	if members == nil {
		_broadcastSimpleEvent(b, "GROUP", gid)
	} else {
		_multicastSimpleEvent(b, "GROUP", gid, members)
	}
}

func sendGroupMessageEvent(b broadcast.Broadcaster, gid string, members []string) {
	_multicastSimpleEvent(b, "GROUP_MESSAGE", gid, members)
}

func sendGroupMessageReadEvent(b broadcast.Broadcaster, gid string, members []string) {
	_multicastSimpleEvent(b, "GROUP_MESSAGE_READ", gid, members)
}

func sendBotEvent(b broadcast.Broadcaster, bid string) {
	_broadcastSimpleEvent(b, "BOT", bid)
}
