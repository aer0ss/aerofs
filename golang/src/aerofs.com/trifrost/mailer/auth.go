package mailer

import (
	"fmt"
	"log"
	"net/smtp"
)

type loginAuth struct {
	user, passwd []byte
}

func LoginAuth(user, passwd string) smtp.Auth {
	return &loginAuth{user: []byte(user), passwd: []byte(passwd)}
}

func (a *loginAuth) Start(s *smtp.ServerInfo) (string, []byte, error) {
	return "LOGIN", a.user, nil
}

func (a *loginAuth) Next(m []byte, more bool) ([]byte, error) {
	if !more {
		return nil, nil
	}
	switch string(m) {
	case "Username:":
		return a.user, nil
	case "Password:":
		return a.passwd, nil
	default:
		return nil, fmt.Errorf("unknown server message %s", string(m))
	}
}

type multiAuth struct {
	available []smtp.Auth
	selected  smtp.Auth
}

func MultiAuth(a ...smtp.Auth) smtp.Auth {
	return &multiAuth{available: a}
}

func (a *multiAuth) Start(s *smtp.ServerInfo) (string, []byte, error) {
	for _, impl := range a.available {
		proto, msg, err := impl.Start(s)
		advertised := false
		for _, supported := range s.Auth {
			if proto == supported {
				advertised = true
				break
			}
		}
		if advertised || proto == "PLAIN" {
			log.Println("auth", proto)
			a.selected = impl
			return proto, msg, err
		}
	}
	return "", nil, fmt.Errorf("no supported auth: %v", s.Auth)
}

func (a *multiAuth) Next(m []byte, more bool) ([]byte, error) {
	return a.selected.Next(m, more)
}
