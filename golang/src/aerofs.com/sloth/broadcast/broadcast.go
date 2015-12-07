// This package provides an interface and default implementation for an
// broadcaster/multicaster of byte strings. Outbound channels are indexed
// by a key (there can be multiple channels per key) and objects can be
// broadcasted to all channels or multicasted to channels indexed by a specific
// set of keys.
package broadcast

type channelSet map[chan []byte]struct{}

// This interface provides methods for subscribing and unsubscribing to a
// stream of objects, and broadcast/multicast methods.
type Broadcaster interface {
	// Returns a channel on which all broadcasted objects will be sent
	Subscribe(string) chan []byte

	// Removes a previously-subscribed channel
	Unsubscribe(chan []byte)

	// Pass an object to send it to all subscribed channels
	Broadcast([]byte)

	// Pass an object to send it to the channels indexed by the given keys
	Multicast([]byte, []string)
}

type subscription struct {
	c   chan []byte
	key string
}

type outbound struct {
	payload []byte
	keys    []string // slice of keys to target, or nil for broadcast
}

type broadcaster struct {
	subs     chan subscription
	unsubs   chan chan []byte
	outbound chan outbound
	channels map[string]channelSet
}

func (b *broadcaster) Subscribe(key string) chan []byte {
	c := make(chan []byte, 1024)
	b.subs <- subscription{c: c, key: key}
	return c
}

func (b *broadcaster) Unsubscribe(c chan []byte) {
	b.unsubs <- c
}

func (b *broadcaster) Broadcast(obj []byte) {
	b.outbound <- outbound{payload: obj}
}

func (b *broadcaster) Multicast(obj []byte, keys []string) {
	b.outbound <- outbound{payload: obj, keys: keys}
}

func (b *broadcaster) selectLoop() {
	for {
		select {
		case s := <-b.subs:
			set, ok := b.channels[s.key]
			if ok {
				// add chan to set
				set[s.c] = struct{}{}
			} else {
				// create new set with chan
				m := make(channelSet)
				m[s.c] = struct{}{}
				b.channels[s.key] = m
			}
		case c := <-b.unsubs:
			// TODO: index this for O(1) unsub?
			for _, set := range b.channels {
				for b := range set {
					if c == b {
						delete(set, c)
					}
				}
			}
		case out := <-b.outbound:
			if out.keys == nil {
				// broadcast
				for _, set := range b.channels {
					broadcastToSet(set, out.payload)
				}
			} else {
				// multicast
				for _, k := range out.keys {
					if set, ok := b.channels[k]; ok {
						broadcastToSet(set, out.payload)
					}
				}
			}
		}
	}
}

func broadcastToSet(set channelSet, payload []byte) {
	for c := range set {
		c <- payload
	}
}

func NewBroadcaster() Broadcaster {
	b := &broadcaster{
		subs:     make(chan subscription, 1024),
		unsubs:   make(chan chan []byte, 1024),
		outbound: make(chan outbound, 1024),
		channels: make(map[string]channelSet),
	}
	go b.selectLoop()
	return b
}
