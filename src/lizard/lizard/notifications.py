import datetime
import json
import smtplib
import requests
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from flask import current_app, render_template, url_for

from . import appliance

# TODO: extract these into external configuration
SENDER_ADDR = "support@aerofs.com"
SALES_ADDR = "sales@aerofs.com"
SLACK_WEBHOOK="https://hooks.slack.com/services/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"

def _make_email_message(email_address, subject, text_body, html_body):
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = SENDER_ADDR
    msg["To"] = email_address

    # Record the MIME types of both parts
    part1 = MIMEText(text_body.encode("utf-8"), "plain", "utf-8") # text/plain
    part2 = MIMEText(html_body.encode("utf-8"), "html", "utf-8") # text/html

    # Attach parts into message container.
    # According to RFC 2046, the last part of a multipart message, in this case
    # the HTML message, is best and preferred.
    msg.attach(part1)
    msg.attach(part2)

    # Add a header to make Sendgrid not replace links with tracking codes, if using sendgrid
    if "sendgrid" in current_app.config["MAIL_SERVER"].lower():
        header = json.dumps({ "filters": {"clicktrack": {"settings": {"enable": 0}}}})
        msg.add_header("X-SMTPAPI", header)

    return msg

def _verification_email_for(email_address, signup_code):
    signup_url = url_for(".signup_completion_page", signup_code=signup_code, _external=True)
    print u"will email verification to {}, link {}".format(email_address, signup_url)

    text_body = render_template("emails/signup_email.txt", signup_url=signup_url)
    html_body = render_template("emails/signup_email.html", signup_url=signup_url)
    return _make_email_message(email_address, "Complete your AeroFS Private Cloud signup",
            text_body, html_body)

def _invite_email_for(email_address, company, invite_code):
    invite_url = url_for(".accept_organization_invite", invite_code=invite_code, _external=True)
    print u"will email invite to {}, link {}".format(email_address, invite_url)

    text_body = render_template("emails/invite_email.txt",
            invite_url=invite_url,
            customer=company,
            )
    html_body = render_template("emails/invite_email.html",
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
            "implementation_video_url": "https://www.youtube.com/watch?v=pVqpobLdoHk",
            "faqs_url": "https://support.aerofs.com/hc/en-us/articles/204592794",
    }
    text_body = render_template("emails/license_ready_email.txt", **template_args)
    html_body = render_template("emails/license_ready_email.html", **template_args)
    return _make_email_message(admin.email, "Your AeroFS Private Cloud License is ready",
            text_body, html_body)

def _password_reset_email_for(email_address, link):
    text_body = render_template("emails/password_reset_email.txt",
            link=link)
    html_body = render_template("emails/password_reset_email.html",
            link=link)
    return _make_email_message(email_address, "AeroFS Private Cloud password reset",
            text_body, html_body)

def _get_mail_connection():
    conn = None
    host = current_app.config["MAIL_SERVER"]
    port = current_app.config["MAIL_PORT"]
    if current_app.config["MAIL_USE_SSL"]:
        conn = smtplib.SMTP_SSL(host, port)
    else:
        conn = smtplib.SMTP(host, port)
    if current_app.config["MAIL_DEBUG"]:
        conn.set_debuglevel(True)

    if current_app.config["MAIL_USE_TLS"]:
        conn.starttls()

    username = current_app.config.get("MAIL_USERNAME", None)
    password = current_app.config.get("MAIL_PASSWORD", None)
    if username or password:
        conn.login(username, password)

    return conn

def _send_email(email_address, msg):
    s = _get_mail_connection()
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

def send_private_cloud_question_email(requester, subject, message):
    text_body = message
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = requester
    msg["To"] = SALES_ADDR
    part1 = MIMEText(text_body.encode("utf-8"), "plain", "utf-8")
    msg.attach(part1)
    _send_email(SALES_ADDR, msg)

def send_sales_notification(email_address, seats):
    msg = MIMEMultipart("alternative")
    text = u"{} upgraded to {} seats".format(email_address, seats)
    msg["Subject"] = text
    msg["From"] = SALES_ADDR
    msg["To"] = SALES_ADDR
    part1 = MIMEText(text.encode("utf-8"), "plain", "utf-8")
    msg.attach(part1)
    _send_email(SALES_ADDR, msg)

def send_internal_appliance_release_notification(appliance_version):
    text = "Release notification: PC {} is now available to the public.".format(appliance_version)
    for room in ["#eng", "#success"]:
        payload = {"text": text, "channel": room, "username": "Release"}
        headers = {"Content-type": "application/json"}
        # N.B. Slack is super picky about the format of the JSON payload. Requests doesn't do it in
        # a way that makes Slack happy, but json.dumps does.
        requests.post(SLACK_WEBHOOK, data=json.dumps(payload), headers=headers)
