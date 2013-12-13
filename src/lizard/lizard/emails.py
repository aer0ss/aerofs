import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from flask import render_template, url_for

# TODO: extract these into external configuration
SENDER_ADDR = "AeroFS <support@aerofs.com>"
SMTP_RELAY = "sv.aerofs.com"

def _make_email_message(email_address, subject, text_body, html_body):
    msg = MIMEMultipart('alternative')
    msg['Subject'] = subject
    msg['From'] = SENDER_ADDR
    msg['To'] = email_address

    # Record the MIME types of both parts
    part1 = MIMEText(text_body, 'plain') # text/plain
    part2 = MIMEText(html_body, 'html') # text/html

    # Attach parts into message container.
    # According to RFC 2046, the last part of a multipart message, in this case
    # the HTML message, is best and preferred.
    msg.attach(part1)
    msg.attach(part2)

    return msg

def _verification_email_for(email_address, signup_code):
    signup_url = url_for('signup_completion_page', signup_code=signup_code, _external=True)
    print u"will email verification to {}, link {}".format(email_address, signup_url)

    text_body = render_template("signup_email.txt", signup_url=signup_url)
    html_body = render_template("signup_email.html", signup_url=signup_url)
    return _make_email_message(email_address, "Complete your AeroFS Private Cloud signup",
            text_body, html_body)

def _invite_email_for(email_address, company, invite_code):
    invite_url = url_for('invite_accept_page', invite_code=invite_code, _external=True)
    print u"will email invite to {}, link {}".format(email_address, invite_url)

    text_body = render_template("invite_email.txt",
            invite_url=invite_url,
            )
    html_body = render_template("invite_email.html",
            invite_url=invite_url,
            )

    return _make_email_message(email_address, "You've been invited to help purchase AeroFS Private Cloud",
            text_body, html_body)

def _send_email(email_address, msg):
    s = smtplib.SMTP(SMTP_RELAY)
    try:
        s.sendmail(SENDER_ADDR, [email_address], msg.as_string())
    finally:
        s.quit()

def send_verification_email(email_address, signup_code):
    msg = _verification_email_for(email_address, signup_code)
    _send_email(email_address, msg)

def send_invite_email(email_address, company, signup_code):
    msg = _invite_email_for(email_address, company, signup_code)
    _send_email(email_address, msg)
