package push

import . "aerofs.com/sloth/structs"

type noopNotifier struct{}

func (n noopNotifier) Notify(body string, uids []string, badge int, cid string) error {
	return nil
}

func (n noopNotifier) NotifyNewMessage(caller *User, targets []string, c *Convo) {
}

func (n noopNotifier) Register(deviceType, alias, token string, dev bool) int {
	return 200
}

func NewNoopNotifier() Notifier {
	return noopNotifier{}
}
