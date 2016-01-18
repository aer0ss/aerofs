package push

import . "aerofs.com/sloth/structs"

type noopNotifier struct{}

func (n noopNotifier) Notify(body string, uids []string, badge int) error {
	return nil
}

func (n noopNotifier) NotifyNewMessage(caller *User, targets []string) {
}

func (n noopNotifier) Register(deviceType, alias, token string, dev bool) int {
	return 200
}

func NewNoopNotifier() Notifier {
	return noopNotifier{}
}
