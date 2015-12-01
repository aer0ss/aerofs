// This package provides an interface and default implementation for an
// broadcaster of arbitrary objects.
package broadcast

// This interface provides methods for subscribing and unsubscribing to a
// stream of objects, and a method to broadcast an object to all subscribed
// channels.
type Broadcaster interface {
	// Returns a channel on which all broadcasted objects will be sent
	Subscribe() chan interface{}

	// Removes a previously-subscribed channel
	Unsubscribe(chan interface{})

	// Pass an object to send it to all subscribed channels
	Broadcast(interface{})
}

type broadcaster struct {
	subs     chan chan interface{}
	unsubs   chan chan interface{}
	outbound chan interface{}
	channels map[chan interface{}]struct{} // why.
}

func (b *broadcaster) Subscribe() chan interface{} {
	c := make(chan interface{}, 1024)
	b.subs <- c
	return c
}

func (b *broadcaster) Unsubscribe(c chan interface{}) {
	b.unsubs <- c
}

func (b *broadcaster) Broadcast(obj interface{}) {
	b.outbound <- obj
}

func (b *broadcaster) selectLoop() {
	for {
		select {
		case c := <-b.subs:
			b.channels[c] = struct{}{}
		case c := <-b.unsubs:
			delete(b.channels, c)
		case obj := <-b.outbound:
			for c := range b.channels {
				c <- obj
			}
		}
	}
}

func NewBroadcaster() Broadcaster {
	b := &broadcaster{
		subs:     make(chan chan interface{}, 1024),
		unsubs:   make(chan chan interface{}, 1024),
		outbound: make(chan interface{}, 1024),
		channels: make(map[chan interface{}]struct{}),
	}
	go b.selectLoop()
	return b
}
