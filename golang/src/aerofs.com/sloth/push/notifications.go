package push

import (
	. "aerofs.com/sloth/structs"
	"fmt"
	"log"
)

// This method is synchronous. Better call it in a separate goroutine.
func (p *notifier) NotifyNewMessage(caller *User, targets []string) {
	body := fmt.Sprintf("%v %v sent a message", caller.FirstName, caller.LastName)
	badge := 0
	err := p.Notify(body, targets, badge)
	if err != nil {
		log.Print("push err: ", err)
	}
}
