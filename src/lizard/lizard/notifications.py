import json
import smtplib
import requests
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from flask import current_app, render_template, url_for

SUPPORT_ADDR = "support@aerofs.com"
SALES_ADDR = "sales@aerofs.com"
SLACK_WEBHOOK="https://hooks.slack.com/services/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"
FAQS_URL = "https://support.aerofs.com/hc/en-us/articles/204592794",

def _make_email_message(email_address, subject, text_body, html_body):
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = SUPPORT_ADDR
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

def _account_already_exists_email_for(admin):
    text_body = render_template("emails/account_already_exists.txt",
            password_reset_url=url_for(".start_password_reset", _external=True),
            admin=admin)
    html_body = render_template("emails/account_already_exists.html",
            password_reset_url=url_for(".start_password_reset", _external=True),
            admin=admin)
    return _make_email_message(admin.email, "Your AeroFS Account", text_body, html_body)

def _account_already_exists_with_promo_email_for(admin, promo_code):
    promo_url = "{}?code={}".format(url_for(".promo", _external=True), promo_code)
    text_body = render_template("emails/account_already_exists_with_promo.txt",
            promo_url=promo_url,
            password_reset_url=url_for(".start_password_reset", _external=True),
            admin=admin)
    html_body = render_template("emails/account_already_exists_with_promo.html",
            promo_url=promo_url,
            password_reset_url=url_for(".start_password_reset", _external=True),
            admin=admin)
    return _make_email_message(admin.email, "Your AeroFS Account", text_body, html_body)

def _account_already_exists_but_inactive_email_for(admin):
    text_body = render_template("emails/account_already_exists_but_inactive.txt",
            contact_support_url="http://ae.ro/1R4hJDN", _external=True,
            admin=admin)
    html_body = render_template("emails/account_already_exists_but_inactive.html",
            contact_support_url="http://ae.ro/1R4hJDN", _external=True,
            admin=admin)
    return _make_email_message(admin.email, "Your AeroFS Account", text_body, html_body)

def _verification_email_for(unbound_signup):
    signup_url = url_for(".signup_completion_page", signup_code=unbound_signup.signup_code, _external=True)
    print u"will email verification to {}, link {}".format(unbound_signup.email, signup_url)
    text_body = render_template("emails/signup_email.txt",
            signup_url=signup_url,
            admin=unbound_signup)
    html_body = render_template("emails/signup_email.html",
            signup_url=signup_url,
            admin=unbound_signup
    )
    return _make_email_message(unbound_signup.email, "Complete your AeroFS Private Cloud signup",
            text_body, html_body)

def _invite_email_for(bound_invite, customer):
    invite_url = url_for(".accept_organization_invite", invite_code=bound_invite.invite_code, _external=True)
    print u"will email invite to {}, link {}".format(bound_invite.email, invite_url)
    text_body = render_template("emails/invite_email.txt",
            faqs_url=FAQS_URL,
            invite_url=invite_url,
            customer=customer,
    )
    html_body = render_template("emails/invite_email.html",
            faqs_url=FAQS_URL,
            invite_url=invite_url,
            customer=customer,
    )
    return _make_email_message(bound_invite.email, "You've been invited to help  manage purchasing for the AeroFS Private Cloud",
            text_body, html_body)

def _license_available_email_for(admin):
    # We can't use url_for() here because it's not part of this server instance
    # (this email is sent from internal app - links would point to the internal
    # app, rather than the user-facing one)
    template_args = {
            "admin": admin,
            "dashboard_url": "https://enterprise.aerofs.com/dashboard",
            "faqs_url": FAQS_URL,
            "contact_url": "https://support.aerofs.com/hc/en-us/articles/201440860"
    }
    text_body = render_template("emails/license_ready_email.txt", **template_args)
    html_body = render_template("emails/license_ready_email.html", **template_args)
    return _make_email_message(admin.email, "Your AeroFS Private Cloud License is ready",
            text_body, html_body)

def _password_reset_email_for(admin, link):
    text_body = render_template("emails/password_reset_email.txt",
            admin=admin,
            link=link)
    html_body = render_template("emails/password_reset_email.html",
            admin=admin,
            link=link)
    return _make_email_message(admin.email, "AeroFS Private Cloud password reset",
            text_body, html_body)

def _hpc_trial_setup_email_for(admin, subdomain):
    text_body = render_template("emails/hpc_trial_setup_email.txt",
            admin=admin,
            subdomain=subdomain,
            faqs_url=FAQS_URL)
    html_body = render_template("emails/hpc_trial_setup_email.html",
            admin=admin,
            subdomain=subdomain,
            faqs_url=FAQS_URL)
    return _make_email_message(admin.email, "AeroFS 30-day Trial Setup Complete",
            text_body,
            html_body)

def _get_mail_connection():
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
        s.sendmail(SUPPORT_ADDR, [email_address], msg.as_string())
    finally:
        s.quit()

def send_account_already_exists_with_promo_email(admin, promo_code):
    msg = _account_already_exists_with_promo_email_for(admin, promo_code)
    _send_email(admin.email, msg)

def send_account_already_exists_email(admin):
    msg = _account_already_exists_email_for(admin)
    _send_email(admin.email, msg)

def send_account_already_exists_but_inactive_email(admin):
    msg = _account_already_exists_but_inactive_email_for(admin)
    _send_email(admin.email, msg)

def send_verification_email(unbound_signup):
    msg = _verification_email_for(unbound_signup)
    _send_email(unbound_signup.email, msg)

def send_invite_email(bound_invite, customer):
    msg = _invite_email_for(bound_invite, customer)
    _send_email(bound_invite.email, msg)

def send_license_available_email(admin):
    msg = _license_available_email_for(admin)
    _send_email(admin.email, msg)

def send_password_reset_email(admin, link):
    msg = _password_reset_email_for(admin, link)
    _send_email(admin.email, msg)

def send_hpc_trial_setup_email(admin, subdomain):
    msg = _hpc_trial_setup_email_for(admin, subdomain)
    _send_email(admin.email, msg)

def send_private_cloud_question_email(requester, to_contact, subject, message):
    to_addr = SALES_ADDR if 'sales' in to_contact.lower() else SUPPORT_ADDR
    text_body = message
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = requester
    msg["To"] = to_addr
    part1 = MIMEText(text_body.encode("utf-8"), "plain", "utf-8")
    msg.attach(part1)
    _send_email(to_addr, msg)

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
    for room in ["#success"]:
        payload = {"text": text, "channel": room, "username": "Release"}
        headers = {"Content-type": "application/json"}
        # N.B. Slack is super picky about the format of the JSON payload. Requests doesn't do it in
        # a way that makes Slack happy, but json.dumps does.
        requests.post(SLACK_WEBHOOK, data=json.dumps(payload), headers=headers)
