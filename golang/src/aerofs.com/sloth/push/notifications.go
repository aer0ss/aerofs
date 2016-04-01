package push

import (
	. "aerofs.com/sloth/structs"
	"fmt"
	"log"
)

// This method is synchronous. Better call it in a separate goroutine.
func (p *notifier) NotifyNewMessage(caller *User, targets []string, convo *Convo) {
	var body string
	switch convo.Type {
	case "CHANNEL":
		body = fmt.Sprintf("%v %v sent a message to %v", caller.FirstName, caller.LastName, convo.Name)
	default:
		body = fmt.Sprintf("%v %v sent you message", caller.FirstName, caller.LastName)
	}
	badge := 0
	err := p.Notify(body, targets, badge, convo.Id)
	if err != nil {
		log.Print("push err: ", err)
	}
}
