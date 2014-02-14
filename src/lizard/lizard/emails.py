import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from flask import render_template, url_for

# TODO: extract these into external configuration
SENDER_ADDR = "AeroFS <support@aerofs.com>"
SMTP_RELAY = "sv.aerofs.com"

SUPPORT_ADDR = "business@aerofs.com"

def _make_email_message(email_address, subject, text_body, html_body):
    msg = MIMEMultipart('alternative')
    msg['Subject'] = subject
    msg['From'] = SENDER_ADDR
    msg['To'] = email_address

    # Record the MIME types of both parts
    part1 = MIMEText(text_body.encode('utf-8'), 'plain', 'utf-8') # text/plain
    part2 = MIMEText(html_body.encode('utf-8'), 'html', 'utf-8') # text/html

    # Attach parts into message container.
    # According to RFC 2046, the last part of a multipart message, in this case
    # the HTML message, is best and preferred.
    msg.attach(part1)
    msg.attach(part2)

    return msg

def _verification_email_for(email_address, signup_code):
    signup_url = url_for('.signup_completion_page', signup_code=signup_code, _external=True)
    print u"will email verification to {}, link {}".format(email_address, signup_url)

    text_body = render_template("signup_email.txt", signup_url=signup_url)
    html_body = render_template("signup_email.html", signup_url=signup_url)
    return _make_email_message(email_address, "Complete your AeroFS Private Cloud signup",
            text_body, html_body)

def _invite_email_for(email_address, company, invite_code):
    invite_url = url_for('.accept_organization_invite', invite_code=invite_code, _external=True)
    print u"will email invite to {}, link {}".format(email_address, invite_url)

    text_body = render_template("invite_email.txt",
            invite_url=invite_url,
            customer=company,
            )
    html_body = render_template("invite_email.html",
            invite_url=invite_url,
            customer=company,
            )

    return _make_email_message(email_address, "You've been invited to help purchase AeroFS Private Cloud",
            text_body, html_body)

def _license_available_email_for(admin, company):
    # We can't use url_for() here because it's not part of this server instance
    # (this email is sent from internal app - links would point to the internal
    # app, rather than the user-facing one)
    dashboard_url = "https://privatecloud.aerofs.com/dashboard"
    template_args = {
            "admin": admin,
            "dashboard_url": dashboard_url,
            "virtualbox_url": "https://www.virtualbox.org/wiki/Downloads",
            "migrating_to_private_url": "https://support.aerofs.com/entries/22978949",
            "implementation_video_url": "https://aerofs.com/product/deployment/private-cloud",
            "faqs_url": "https://support.aerofs.com/forums/20877659-Getting-Started-with-Private-Cloud",
    }
    text_body = render_template("license_ready_email.txt", **template_args)
    html_body = render_template("license_ready_email.html", **template_args)
    return _make_email_message(admin.email, "Your AeroFS Private Cloud License is ready",
            text_body, html_body)

def _password_reset_email_for(email_address, link):
    text_body = render_template("password_reset_email.txt",
            link=link)
    html_body = render_template("password_reset_email.html",
            link=link)
    return _make_email_message(email_address, "AeroFS Private Cloud password reset",
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

def send_invite_email(email_address, company, invite_code):
    msg = _invite_email_for(email_address, company, invite_code)
    _send_email(email_address, msg)

def send_license_available_email(admin, company):
    msg = _license_available_email_for(admin, company)
    _send_email(admin.email, msg)

def send_password_reset_email(email_address, link):
    msg = _password_reset_email_for(email_address, link)
    _send_email(email_address, msg)

def send_support_request_email(requester, message):
    subject = "[Private Cloud Support] - {}".format(requester)
    text_body = message
    msg = MIMEMultipart('alternative')
    msg['Subject'] = subject
    msg['From'] = SENDER_ADDR
    msg['To'] = SUPPORT_ADDR
    part1 = MIMEText(text_body.encode('utf-8'), 'plain', 'utf-8')
    msg.attach(part1)
    _send_email(SUPPORT_ADDR, msg)
