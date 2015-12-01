package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"encoding/json"
)

func broadcastUserEvent(b broadcast.Broadcaster, uid string) {
	bytes, err := json.Marshal(Event{
		Resource: "USER",
		Id:       uid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastUserMessageEvent(b broadcast.Broadcaster, uid string) {
	bytes, err := json.Marshal(Event{
		Resource: "USER_MESSAGE",
		//FIXME: this'll change when we change broadcast to auth'd multicast
		Id: uid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastUserAvatarEvent(b broadcast.Broadcaster, uid string) {
	bytes, err := json.Marshal(Event{
		Resource: "USER_AVATAR",
		Id:       uid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastUserMessageReadEvent(b broadcast.Broadcaster, uid string) {
	bytes, err := json.Marshal(Event{
		Resource: "USER_MESSAGE_READ",
		Id:       uid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastGroupEvent(b broadcast.Broadcaster, gid string) {
	bytes, err := json.Marshal(Event{
		Resource: "GROUP",
		Id:       gid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastGroupMessageEvent(b broadcast.Broadcaster, gid string) {
	bytes, err := json.Marshal(Event{
		Resource: "GROUP_MESSAGE",
		Id:       gid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastGroupMessageReadEvent(b broadcast.Broadcaster, gid string) {
	bytes, err := json.Marshal(Event{
		Resource: "GROUP_MESSAGE_READ",
		Id:       gid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}

func broadcastBotEvent(b broadcast.Broadcaster, bid string) {
	bytes, err := json.Marshal(Event{
		Resource: "BOT",
		Id:       bid,
	})
	errors.PanicOnErr(err)
	b.Broadcast(bytes)
}
