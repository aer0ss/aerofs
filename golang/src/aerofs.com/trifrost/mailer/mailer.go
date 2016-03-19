package mailer

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"log"
	"net/smtp"
	"time"
)

const (
	// buffer size for email chan
	MAIL_QUEUE_SIZE = 1000

	// time to wait before between connect retries
	MAIL_RECONNECT_WAIT = time.Second
)

type emailCodePair struct {
	email string
	code  int
}

type Mailer interface {

	// Queue up an email/code pair for mailing
	Mail(email string, code int)
}

// FromConfig returns a connected Mailer given a map of config values
func FromConfig(config map[string]string) Mailer {
	mailer := &mailer{
		applianceHost: config["base.host.unified"],
		host:          config["email.sender.public_host"],
		port:          config["email.sender.public_port"],
		sender:        config["base.www.support_email_address"],
		user:          config["email.sender.public_username"],
		pass:          config["email.sender.public_password"],
		enableTls:     config["email.sender.public_enable_tls"] != "False",
		queue:         make(chan emailCodePair, MAIL_QUEUE_SIZE),
	}

	log.Print("smtp host: ", mailer.host)
	log.Print("smtp port: ", mailer.port)
	log.Print("smtp sender: ", mailer.sender)
	log.Print("smtp user: ", mailer.user)
	log.Print("smtp pass: ", mailer.pass)
	log.Print("smtp enable TLS: ", mailer.enableTls)

	if mailer.enableTls {
		mailer.tlsConfig = getTLSConfig(config)
	}

	mailer.reconnect()

	go mailer.sendLoop()
	return mailer
}

type mailer struct {
	applianceHost string
	host, port    string
	sender        string
	user, pass    string
	enableTls     bool

	client    *smtp.Client
	queue     chan emailCodePair
	tlsConfig *tls.Config
}

func (m *mailer) Mail(email string, code int) {
	m.queue <- emailCodePair{email: email, code: code}
}

func (m *mailer) reconnect() {
	for {
		err := m.tryReconnect()
		if err == nil {
			return
		} else {
			log.Print("smtp err: ", err)
			time.Sleep(MAIL_RECONNECT_WAIT)
		}
	}
}

func (m *mailer) tryReconnect() error {
	m.client = nil

	addr := fmt.Sprint(m.host, ":", m.port)

	log.Print("smtp: connecting to ", addr)
	client, err := smtp.Dial(addr)
	if err != nil {
		return err
	}

	log.Println("smtp: saying HELO...")
	if err := client.Hello(m.applianceHost); err != nil {
		return err
	}

	if m.enableTls {
		log.Println("smtp: upgrading to tls...")
		if err := client.StartTLS(m.tlsConfig); err != nil {
			return err
		}
	} else {
		log.Println("smtp: skipping tls upgrade...")
	}

	if m.user != "" && m.pass != "" {
		log.Println("smtp: authenticating...")
		auth := smtp.PlainAuth("", m.user, m.pass, m.host)
		if err := client.Auth(auth); err != nil {
			return err
		}
	}

	m.client = client
	return nil
}

func (m *mailer) sendLoop() {
	for {
		p := <-m.queue
		log.Printf("sending code %v to %v\n", p.code, p.email)
		if err := m.client.Mail(m.sender); err != nil {
			log.Print("err in client.Mail: ", err)
			m.queue <- p
			m.reconnect()
			continue
		}
		if err := m.client.Rcpt(p.email); err != nil {
			log.Print("err in client.Rcpt: ", err)
			m.queue <- p
			m.reconnect()
			continue
		}
		wc, err := m.client.Data()
		if err != nil {
			log.Print("err in client.Data: ", err)
			m.queue <- p
			m.reconnect()
			continue
		}
		body := fmt.Sprint(
			"From: AeroFS <", m.sender, ">\r\n",
			"To: ", p.email, "\r\n",
			"Subject: Your Eyja Authorization Code: ", p.code, "\r\n",
			"Enter this authorization code to access Eyja: ", p.code, "\r\n",
		)
		if _, err := wc.Write([]byte(body)); err != nil {
			log.Print("err writing email body: ", err)
			m.queue <- p
			m.reconnect()
			continue
		}
		if err := wc.Close(); err != nil {
			log.Print("err closing email body: ", err)
			m.queue <- p
			m.reconnect()
			continue
		}
	}
}

func getTLSConfig(config map[string]string) *tls.Config {
	cert := config["email.sender.public_cert"]
	host := config["email.sender.public_host"]
	if cert == "" {
		return &tls.Config{
			ServerName: host,
		}
	}
	certPool := x509.NewCertPool()
	if !certPool.AppendCertsFromPEM([]byte(cert)) {
		log.Panic("unable to parse cert")
	}
	return &tls.Config{
		ServerName: host,
		RootCAs:    certPool,
	}

}
