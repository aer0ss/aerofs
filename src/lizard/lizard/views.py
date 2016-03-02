import base64
import time
import os
import json
import requests
import datetime
from itsdangerous import TimestampSigner
import itsdangerous
import markupsafe
import stripe
import flask_scrypt as scrypt
import flask_login as login
import flask_api.status
from werkzeug.datastructures import MultiDict

from flask import Blueprint, abort, current_app, render_template, flash, redirect, request, \
    url_for, Response, session

from lizard import analytics_client, db, login_manager, csrf, login_helper, password_reset_helper, \
    appliance, notifications, forms, models, promotions, hpc

blueprint = Blueprint('main', __name__, template_folder='templates')

subscriptions = {
    'aerofs_monthly': {
        'description': u'AeroFS Annual Subscription (billed monthly)',
        'price': int(15)
    },
    'aerofs_annual': {
        'description': u'AeroFS Annual Subscription (billed yearly)',
        'price': int(180)
    }
}

@login_manager.user_loader
def load_user(userid):
    return models.Admin.query.filter_by(email=userid).first()

@blueprint.route('/', methods=['GET'])
@blueprint.route('/index', methods=['GET'])
def index():
    # TODO: Enable this when we release the 30-day trial
    # return redirect(url_for(".get_hpc"))
    return redirect(url_for(".dashboard"))

@blueprint.route('/login', methods=['GET', 'POST'])
def login_page():
    form = forms.LoginForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=form.email.data).first()
        if not admin or not admin.active:
            # This user doesn't exist.
            flash(u"Email or password is incorrect", 'error')
        elif not scrypt.check_password_hash(form.password.data.encode('utf8'), admin.pw_hash.encode('utf8'), admin.salt.encode('utf8')):
            # The password was wrong.
            flash(u"Email or password is incorrect", 'error')
        else:
            # Successful login.
            login_success = login_helper.login_user(admin)
            # TODO: handle inactive users more clearly?  not sure what UX to expose in that case.
            if login_success:
                analytics_client.track(admin.customer_id, "Logged In", {
                    'email': markupsafe.escape(admin.email)
                })
            next_url = request.args.get('next') or url_for(".index")
            # sanitize next_url to ensure that it's relative.  Avoids user
            # redirection attacks where you log in and then get redirected to an
            # evil site.
            if not next_url.startswith('/'):
                next_url = url_for(".index")
            # Ensuring that next_url starts with / is not enough; it could be a protocol-relative link
            # like //google.com.  Ensure that we set the host for the redirect properly.
            target_url = request.host_url[:-1] + next_url # Strip the trailing / from request.host_url
            return redirect(target_url)
    return render_template("login.html",
            form=form)


@blueprint.route("/logout", methods=["POST"])
def logout():
    login_helper.logout_user_this_session()
    flash(u"You have logged out successfully", 'success')
    return redirect(url_for(".index"))


@csrf.exempt
@blueprint.route("/request_signup", methods=["POST"])
def signup_request_page():
    """
    GET /request_signup - shows form for signing up
    POST /request_signup
    """
    form = forms.SignupForm(csrf_enabled=False)
    if form.validate_on_submit():
        request_signup(form)
        return redirect(url_for(".signup_request_done"))
    else:
        return '', flask_api.status.HTTP_400_BAD_REQUEST


@csrf.exempt
@blueprint.route("/request_signup_headless", methods=["POST"])
def signup_request_headless_page():
    form = forms.SignupForm(csrf_enabled=False)
    if form.validate_on_submit():
        request_signup(form)
        return '', flask_api.status.HTTP_204_NO_CONTENT
    return '', flask_api.status.HTTP_400_BAD_REQUEST


def request_signup(form):
    # If email already in Admin table, noop (but return success). We don't want to leak that an
    # account bound to an email exists by returning an error.
    # If email already in Admin table but account is inactive, notify user via email that the
    # the account is inactive.
    admin = models.Admin.query.filter_by(email=form.email.data).first()
    if admin:
        if form.promo_code.data is not None and len(form.promo_code.data) > 0:
            notifications.send_account_already_exists_with_promo_email(admin, form.promo_code.data)
        elif not admin.active:
            notifications.send_account_already_exists_but_inactive(admin)
        else:
            notifications.send_account_already_exists_email(admin)
        return
    # If email already in UnboundSignup, just fetch that record.
    record = models.UnboundSignup.query.filter_by(email=form.email.data).first()
    if not record:
        # Create random signup code. 30 bytes (240 bits) of randomness. Could do 32, but using a
        # multiple of 3 means we avoid having base64 padding (==) in urls. Note email verification
        # codes probably shouldn't be typed in  manually. If you're typing links out by hand, you
        # deserve the pain you're in.
        signup_code = base64.urlsafe_b64encode(os.urandom(30))
        # Insert code/email pair into UnboundSignup.
        record = models.UnboundSignup()
        record.email=form.email.data
        record.signup_code=signup_code
        record.first_name = form.first_name.data.capitalize()
        record.last_name = form.last_name.data.capitalize()
        record.company_name = form.company_name.data
        record.phone_number = form.phone_number.data
        record.job_title = form.job_title.data
        record.promo_code = form.promo_code.data
        db.session.add(record)
        db.session.commit()

    notifications.send_verification_email(record)
    pardot_params = {
        'first_name': record.first_name.encode('utf-8'),
        'last_name': record.last_name.encode('utf-8'),
        'email': record.email.encode('utf-8'),
        'company': record.company_name.encode('utf-8'),
        'phone': record.phone_number.encode('utf-8'),
        'job_title': record.job_title.encode('utf-8'),
        'company_size': form.company_size.data.encode('utf-8'),
        'current_fss': form.current_fss.data.encode('utf-8'),
        'country': form.country.data.encode('utf-8'),
        'price_plan': form.price_plan.data.encode('utf-8'),
        'promo_code': form.promo_code.data.encode('utf-8'),
        'demandramp_rm__utm_medium__c': form.demandramp_rm__utm_medium__c.data.encode('utf-8'),
        'demandramp_rm__utm_source__c': form.demandramp_rm__utm_source__c.data.encode('utf-8'),
        'demandramp_rm__utm_campaign__c': form.demandramp_rm__utm_campaign__c.data.encode('utf-8'),
        'demandramp_rm__utm_content__c': form.demandramp_rm__utm_content__c.data.encode('utf-8'),
        'demandramp_rm__utm_term__c': form.demandramp_rm__utm_term__c.data.encode('utf-8'),
        'demandramp_rm__referring_url__c': form.demandramp_rm__referring_url__c.data.encode('utf-8'),
        'demandramp_rm__destination_url__c': form.demandramp_rm__destination_url__c.data.encode('utf-8'),
        'demandramp_rm__form_fill_out_url__c': form.demandramp_rm__form_fill_out_url__c.data.encode('utf-8'),
        'demandramp_rm__landing_page_url__c': form.demandramp_rm__landing_page_url__c.data.encode('utf-8'),
        'demandramp_rm__person_id__c': form.demandramp_rm__person_id__c.data.encode('utf-8'),
        'demandramp_rm__session_id__c': form.demandramp_rm__session_id__c.data.encode('utf-8')
    }
    requests.get("https://go.pardot.com/l/32882/2014-03-27/bjxp", params=pardot_params)


@blueprint.route("/request_signup_done", methods=["GET"])
def signup_request_done():
    return render_template("request_signup_complete.html")


@blueprint.route("/signup", methods=["GET", "POST"])
def signup_completion_page():
    """
    GET /signup?code=<access_code>
    POST /signup?code=<access_code> <form data>
    """
    user_signup_code = request.args.get("signup_code", None)

    if not user_signup_code:
        # return to the "enter your email so we can verify it" page
        flash("That link didn't include a signup code.", "error")
        return redirect("http://www.aerofs.com/signup-continue/")

    signup = models.UnboundSignup.query.filter_by(signup_code=user_signup_code).first()
    if not signup:
        # This signup code was invalid.
        flash("That signup code is invalid or has already been used.", "error")
        return redirect(url_for(".login_page"))

    form = forms.CompleteSignupForm()
    if form.validate_on_submit():
        # Create a new Customer named `company`
        cust = models.Customer()
        cust.name = signup.company_name
        cust.renewal_seats = 30 # default renewal number
        db.session.add(cust)

        # Create a new Admin with the right
        # `first_name`, `last_name`, `customer_id`,
        # `phone_number`, `job_title`,
        # `salt`, `pw_hash`
        admin = models.Admin()
        admin.customer = cust
        admin.email = signup.email
        admin.first_name = signup.first_name
        admin.last_name = signup.last_name
        admin.phone_number = signup.phone_number
        admin.job_title = signup.job_title
        admin.set_password(form.password.data)
        db.session.add(admin)

        # Delete any invite codes for that user from the database; they can no
        # longer accept an invitation from a different organization.
        db.session.query(models.BoundInvite).filter(models.BoundInvite.email==signup.email).delete()
        # Delete the signup code from the database, as it is consumed
        db.session.delete(signup)

        # Create a new License request for the customer that just signed up
        l = models.License()
        l.customer = cust
        l.state = models.License.LicenseState.PENDING
        l.seats = 30 # Default to 30 seat trial licenses
        l.set_days_until_expiry(365*10) # default to 10 years for now
        l.is_trial = True
        l.allow_audit = False
        l.allow_identity = False
        l.allow_mdm = False
        l.allow_device_restriction = False

        db.session.add(l)

        # Commit.
        db.session.commit()

        # Submit analytics tracking thing.
        analytics_client.identify(admin.customer_id,
                {
                    'email': markupsafe.escape(admin.email),
                    'firstName': markupsafe.escape(admin.first_name),
                    'lastName': markupsafe.escape(admin.last_name),
                    'company': markupsafe.escape(admin.customer.name),
                    'title': markupsafe.escape(admin.job_title),
                    'phone': markupsafe.escape(admin.phone_number),
                    'Enterprise': 'true',
                })
        analytics_client.track(admin.customer_id, "Signed Up For Private Cloud", {
            'email': markupsafe.escape(admin.email)
            })

        # Log user in.
        login_success = login_helper.login_user(admin)
        if not login_success:
            flash(u"Login failed for {}: probably marked inactive?", "error")

        if signup.promo_code:
            return redirect("{}?code={}".format(url_for(".promo"), signup.promo_code))
        else:
            return redirect(url_for(".index"))

    return render_template("complete_signup.html",
        form=form,
        record=signup,
    )

@blueprint.route("/account", methods=["GET", "POST"])
@login.login_required
def edit_preferences():

    user = login.current_user
    form = forms.PreferencesForm()
    if form.validate_on_submit():
        # Update name
        user.first_name = form.first_name.data
        user.last_name = form.last_name.data
        user.customer.name = form.customer_name.data
        # Update email preferences (disabled until we add it back to the form)
        #user.notify_security    = form.security_emails.data
        #user.notify_release     = form.release_emails.data
        #user.notify_maintenance = form.maintenance_emails.data
        # Save to DB
        db.session.add(user)
        db.session.commit()
        flash(u'Saved changes.', 'success')
        return redirect(url_for(".edit_preferences"))
    form.first_name.data = user.first_name
    form.last_name.data = user.last_name
    form.customer_name.data = user.customer.name
    # (disabled until we enable it in the form again)
    #form.security_emails.data = user.notify_security
    #form.release_emails.data = user.notify_release
    #form.maintenance_emails.data = user.notify_maintenance

    return render_template("preferences.html",
        form=form,
        user=user
    )

@blueprint.route("/deactivate_admin_account", methods=["POST"])
def deactivate_admin_account():

    admin = login.current_user
    admin.active = '0'
    admin.pw_hash = 'NULL'
    db.session.commit()
    flash(u'Account deactivated.', 'success')

    return redirect(url_for(".login_page"))

@blueprint.route("/users/invitation", methods=["POST"])
@login.login_required
def invite_to_organization():
    user = login.current_user
    customer = user.customer
    form = forms.InviteForm()
    if form.validate_on_submit():
        email = form.email.data
        # Verify that the email is not already in the Admin table
        if models.Admin.query.filter_by(email=form.email.data).first():
            flash(u'That user is already a member of an organization', 'error')
            return redirect(url_for(".users"))
        # Either this user has been invited to the organization already or hasn't.
        # (Since multiple organizations may attempt to invite the same user, we
        # need to filter on both email and org id in the query here.)
        # If she has been invited before (record is not None), we probably want
        # to resend her email invitation because she lost the first one.
        # If she hasn't (record is None), we need to generate a BoundInvite and
        # email it to her.
        bound_invite = models.BoundInvite.query.filter_by(email=email, customer_id=customer.id).first()
        if not bound_invite:
            invite_code = base64.urlsafe_b64encode(os.urandom(30))
            bound_invite = models.BoundInvite()
            bound_invite.invite_code = invite_code
            bound_invite.email = email
            bound_invite.customer_id = customer.id
            db.session.add(bound_invite)
            db.session.commit()
        # Send the invite email
        notifications.send_invite_email(bound_invite, customer)

        flash(u'Invited {} to join {}'.format(email, customer.name), 'success')
        return redirect(url_for('.users'))
    else:
        flash(form.email.errors[0], "error")
        return redirect(url_for('.users'))

@blueprint.route('/users/accept', methods=["GET", "POST"])
def accept_organization_invite():
    user_invite_code = request.args.get("invite_code", None)
    if not user_invite_code:
        # bogus accept code, send user home
        return redirect(url_for(".index"))
    invite = models.BoundInvite.query.filter_by(invite_code=user_invite_code).first()
    if not invite:
        # This invite code was invalid.
        return redirect(url_for(".index"))
    customer = models.Customer.query.get(invite.customer_id)

    form = forms.AcceptInviteForm()
    if form.validate_on_submit():
        # Create a new Admin with the right fields
        admin = models.Admin()
        admin.customer = customer
        admin.email = invite.email
        admin.first_name = form.first_name.data
        admin.last_name = form.last_name.data
        admin.phone_number = form.phone_number.data
        admin.job_title = form.job_title.data
        admin.set_password(form.password.data)
        db.session.add(admin)

        # Delete all invite and signup codes associated with this email
        # address.  You can only accept one.
        db.session.query(models.BoundInvite).filter(models.BoundInvite.email==invite.email).delete()
        db.session.query(models.UnboundSignup).filter(models.UnboundSignup.email==invite.email).delete()

        # Commit.
        db.session.commit()

        # Log user in.
        login_success = login_helper.login_user(admin)
        if not login_success:
            flash(u"Login failed for {}: probably marked inactive?", 'error')

        return redirect(url_for(".index"))

    return render_template("accept_invite.html",
            form=form,
            invite=invite,
            customer=customer)

@blueprint.route("/pay", methods=["POST"])
@login.login_required
def pay():
    user = login.current_user
    customer = user.customer
    newest_license = customer.newest_license()
    billing_frequency = request.form['billing_frequency']
    requested_license_count = request.form['requested_license_count']
    license_request = models.License()
    license_request.customer = customer
    license_request.state = models.License.LicenseState.PENDING
    license_request.seats = requested_license_count
    license_request.is_trial = False
    license_request.allow_audit = True
    license_request.allow_identity = True
    license_request.allow_mdm = True
    license_request.allow_device_restriction = True
    # charge the customer
    msg=None
    try:
        #create/retrieve stripe customer
        if not customer.stripe_customer_id:
            stripe_customer = stripe.Customer.create(
                    email=user.email,
                    card=request.form['stripeToken']
                    )

            customer.stripe_customer_id = stripe_customer.id
            db.session.add(customer)
        # create/update subscription
        stripe_customer = stripe.Customer.retrieve(customer.stripe_customer_id)
        if newest_license.is_trial: # new subscription
            license_request.set_days_until_expiry(365)

            stripe_subscription = stripe_customer.subscriptions.create(
                plan=billing_frequency,
                quantity=license_request.seats)
            license_request.stripe_subscription_id = stripe_subscription.id
        else: # Pro-rate.
            license_request.expiry_date = newest_license.expiry_date
            license_request.stripe_subscription_id = newest_license.stripe_subscription_id
            stripe_subscription = stripe_customer.subscriptions.retrieve(newest_license.stripe_subscription_id)
            stripe_subscription.prorate = "true"
            stripe_subscription.quantity = license_request.seats
            stripe_subscription.save()
            # Immediattely charge the customer (otherwise the customer will get charged at the end of the billing period).
            try:
                stripe.Invoice.create(customer=stripe_customer.id).pay()
            except (stripe.CardError, stripe.StripeError) as e:
                # Try and roll back the subscription if the invoice payment failed.
                # (Why stripe throws an error as opposed to a null invoice here is beyond me)
                if customer.stripe_customer_id and not newest_license.is_trial:
                    stripe_customer = stripe.Customer.retrieve(customer.stripe_customer_id)
                    stripe_subscription = stripe_customer.subscriptions.retrieve(newest_license.stripe_subscription_id)
                    stripe_subscription.prorate = "false"
                    stripe_subscription.quantity = newest_license.seats
                    stripe_subscription.save()
                raise
    except stripe.CardError as e:
        # Since it's a decline, stripe.error.CardError will be caught.
        body = e.json_body
        err  = body['error']
        msg = err['message']
    except stripe.StripeError as e:
        msg = u"An unknown error has occured and your card has not been charged. Please try again later."
        current_app.logger.warn(e)
    except Exception as e:
        msg = u"An internal server error has occured and your card has not been charged. If you continue seeing this error, please contact us at support@aerofs.com."
        current_app.logger.warn(e)
    else:
        # Charged succesfully, create license request.
        db.session.add(license_request)
        customer.renewal_seats = license_request.seats
        db.session.add(customer)
        # Commit.
        db.session.commit()
        if newest_license.is_trial:
            flash(u"Successfully bought {} seats.".format(license_request.seats), 'success')
        else:
            flash(u"Your upgrade from {} seats to {} seats is being processed".format(newest_license.seats,requested_license_count), 'success')

        notifications.send_sales_notification(user.email, license_request.seats)

        analytics_client.track(user.customer_id, 'Bought seats', {
            'email': markupsafe.escape(user.email),
            'quantity': license_request.seats,
            'billing_frequency': billing_frequency
        })
    finally:
        if msg: #there's an error
            flash(msg, "error")
            return redirect(url_for('.buy'))

    session.pop('requested_license_count', None)
    return redirect(url_for('.dashboard'))

@blueprint.route("/buy", methods=["GET","POST"])
@login.login_required
def buy():
    user = login.current_user
    billing_frequency = request.args.get("billing_frequency", "aerofs_monthly")
    if billing_frequency not in subscriptions:
            billing_frequency = "aerofs_monthly"

    if 'requested_license_count' in session:
        requested_license_count= session['requested_license_count']
    else:
        flash(u"Please select a license count and try again.", "error")
        return redirect(url_for('.dashboard'))

    customer = user.customer
    newest_license = customer.newest_license()
    order_summary = {}

    if newest_license.is_trial: #new subscription

        charge_amount = requested_license_count * subscriptions.get(billing_frequency).get("price")
        description = "Purchase "  + str(requested_license_count) +" seats"

        charges = [{
                    "amount": charge_amount * 100,
                    "description": description
                }]

        total = charge_amount * 100
        order_summary["charges"] = charges
        order_summary["total"] = total
    else: #pro-rate
        if requested_license_count <= newest_license.seats:
            flash (u"Something went wrong. Your requested license count is lower than your existing license count.", "error")
            return redirect(url_for('.dashboard'))

        if not customer.stripe_customer_id or not newest_license.stripe_subscription_id:
            flash (u"It looks like your account is not set up for credit card billing. Please contact billing@aerofs.com to upgrade.", "error")
            return redirect(url_for('.dashboard'))

        # retrieve existing subscription and display prorated charge
        stripe_customer = stripe.Customer.retrieve(customer.stripe_customer_id)
        stripe_subscription = stripe_customer.subscriptions.retrieve(newest_license.stripe_subscription_id)
        upcoming_invoice = stripe.Invoice.upcoming(
            customer=customer.stripe_customer_id,
            subscription=stripe_subscription.id,
            subscription_quantity=requested_license_count,
            subscription_prorate="true",
            subscription_proration_date=int(time.time())
        )

        charges = []
        amount_due = 0
        for invoice_item in upcoming_invoice.lines.data:
            # by default, stripe assumes the upcoming invoice
            # will be billed at the next billing cycle. I prefer to bill them
            # immeddiately so that we don't generate a license key if they can't pay
            # but because of stripes assumption, in the "upcoming invoice" function they
            # return the next susbscription payment too. We want to only show invoice items,
            # not subscription billings as well.

            if invoice_item.type == "invoiceitem":
                charge = {
                    "amount": invoice_item.amount,
                    "description": invoice_item.description
                }
                charges.append(charge)
                amount_due = amount_due + invoice_item.amount
        order_summary["charges"] = charges
        order_summary["total"] = amount_due

        # sanity check to make sure the order didn't end up being 0
        if amount_due == 0:
            flash (u"Failed to generate a new invoice. Please try again later, and contact support@aerofs.com if the error persists", "error")
            return redirect(url_for('.dashboard'))

    analytics_client.track(user.customer_id, 'Visited Buy Page', {
        'email': markupsafe.escape(user.email),
        'order_summary': order_summary,
        'billing_frequency': billing_frequency
    })
    return render_template("buy.html",
        current_license=newest_license,
        requested_license_count = requested_license_count,
        order_summary = order_summary,
        email=user.email,
        subscriptions=subscriptions,
        billing_frequency=billing_frequency,
        stripe_customer_id=customer.stripe_customer_id
        )

@blueprint.route("/billing", methods=["GET", "POST"])
@login.login_required
def billing():
    user = login.current_user

    charges = []
    card = None
    msg = None
    try:
        if request.method == 'POST' and request.form['stripeToken']:
            token = request.form['stripeToken']
            if user.customer.stripe_customer_id: # if customer exists, create a new default card
                stripe_customer = stripe.Customer.retrieve(user.customer.stripe_customer_id)

                card = stripe_customer.sources.create(source=token)
                stripe_customer.default_source = card.id
                stripe_customer.save()
            else: #create customer
                stripe_customer = stripe.Customer.create(
                    description=user.email,
                    email=user.email,
                    source=token)
                customer = user.customer
                customer.stripe_customer_id = stripe_customer.id
                db.session.add(customer)
                db.session.commit()
            flash(u"Successfully updated your credit card", "success")
            return redirect(url_for(".billing"))

        # retrieve default card to display
        if user.customer.stripe_customer_id:
            charges = stripe.Charge.all(customer=user.customer.stripe_customer_id).data
            stripe_customer = stripe.Customer.retrieve(user.customer.stripe_customer_id)
            default_card = stripe_customer.default_source
            if default_card:
                card = stripe_customer.sources.retrieve(default_card)
    except stripe.CardError as e:
        # Since it's a decline, stripe.error.CardError will be caught
        body = e.json_body
        err  = body['error']
        msg = err['message']
    except stripe.StripeError as e:
        msg = u"An unknown error has occurred and your card has not been changed. Please try again later."
        current_app.logger.warn(e)
    except Exception as e:
        msg = u"An internal server error has occured and your card has not been changed. If you continue seeing this error, please contact us at support@aerofs.com."
        current_app.logger.warn(e)
    finally:
        if msg:
            flash(msg, "error")
            return redirect(url_for('.billing'))

    return render_template("billing.html",
        charges=charges,
        card=card,
        stripe_pk=current_app.config['STRIPE_PUBLISHABLE_KEY']
    )


@blueprint.route("/receipt/<string:id>", methods=["GET"])
@login.login_required
def receipt(id):
    user = login.current_user

    charge = stripe.Charge.retrieve(id)
    if charge.customer != user.customer.stripe_customer_id:
            flash(u"This receipt does not exist.", "error")
            return redirect(url_for(".billing"))

    line_items = stripe.Invoice.retrieve(charge.invoice).lines.all().data
    return render_template("receipt.html",
        charge=charge,
        line_items=line_items
    )


@blueprint.route("/promo", methods=["GET", "POST"])
@login.login_required
def promo():
    form = forms.PromoForm()

    if form.validate_on_submit():
        user = login.current_user
        customer = user.customer

        pardot_params = {
        'email': markupsafe.escape(user.email),
        'promo_code': form.code.data.encode('utf-8')
        }

        requests.get("https://go.pardot.com/l/32882/2016-01-20/4nxvsz", params=pardot_params)

        return promotions.post_promo(form.code.data)
    return promotions.get_promo(request.args.get('code', ''))

@blueprint.route("/dashboard", methods=["GET", "POST"])
@login.login_required
def dashboard():
    user = login.current_user
    customer = user.customer
    newest_license = customer.newest_license()

    analytics_client.track(user.customer_id, 'Visited Dashboard', {
        'email': markupsafe.escape(user.email),
    })
    if newest_license is None:
        flash('Sorry, we found a problem with your license request. Please contact us at support@aerofs.com', "error")
        return redirect(url_for(".login_page"))

    if newest_license.is_trial:
        form = forms.LicenseCountForm(count=1)
    else:
        form = forms.LicenseCountForm(count=(customer.renewal_seats or newest_license.seats)+5) #default to a 5 more seats than we already have for purchase
    if form.validate_on_submit():
        requested_license_count = int(form.count.data)

        if newest_license.is_trial:
            # if current license is a trial, no matter what the seat count is, we're buying
            session['requested_license_count'] = requested_license_count
            return redirect(url_for(".buy"))

        if requested_license_count < int(newest_license.seats): # we are downgrading

            # Update the renewal seats
            customer.renewal_seats = requested_license_count
            db.session.add(customer)
            db.session.commit()

            flash(u"Your license will downgrade to {} seats on {}"
                .format(requested_license_count, newest_license.expiry_date.strftime('%b %d, %Y')),
                "success")
            return redirect(url_for(".dashboard"))

        elif requested_license_count > int(newest_license.seats): #we are upgrading
            session['requested_license_count'] = requested_license_count
            return redirect(url_for(".buy"))

        else:
            # Always update the renewal seats
            customer.renewal_seats = requested_license_count
            db.session.add(customer)
            db.session.commit()

            flash(u"You already have {} seats".format(requested_license_count), "success")
            return redirect(url_for(".dashboard"))
    return render_template("dashboard.html",
        form=form,
        active_license=customer.newest_filled_license(),
        newest_license=newest_license,
        renewal_seats=(customer.renewal_seats or newest_license.seats),
        appliance_version=appliance.latest_appliance_version()
    )

@blueprint.route("/users", methods=["GET"])
@login.login_required
def users():
    return render_template("users.html",
        user=login.current_user,
        form=forms.InviteForm(),
    )

@blueprint.route("/version", methods=["GET"])
def version():
    return appliance.latest_appliance_version()

@blueprint.route("/hosted_private_cloud", methods=["GET"])
@login.login_required
def get_hpc():
    customer = login.current_user.customer
    hpc = models.HPCDeployment.query.filter_by(customer_id=customer.id).first()
    setup_timeout_in_minutes = 60;
    template_data = {
        "hpc_started": False,
        "appliance_up": False,
        "appliance_error": False,
        "hpc_days_left": 30,
        "hpc_url": '',
    }

    if hpc is not None:
        now = datetime.datetime.today()
        template_data["hpc_started"] = True
        template_data["hpc_url"] += 'https://' + hpc.full_hostname()
        template_data["hpc_days_left"] = hpc.get_days_until_expiry()

        if hpc.appliance_setup_date is not None:
            template_data["appliance_up"] = True
        else:
            template_data["appliance_error"] = now > hpc.create_date + datetime.timedelta(minutes=setup_timeout_in_minutes)

    analytics_client.track(customer.id, "Visited Trial Page", {
        'email': markupsafe.escape(login.current_user.email),
        'started': template_data["hpc_started"],
        'days_left': template_data["hpc_days_left"]
    })

    return render_template("hpc.html", **template_data)

@blueprint.route("/hosted_private_cloud", methods=["POST"])
@login.login_required
def submit_subdomain():
    customer = login.current_user.customer

    # We already have the customer data
    # Just add it to the form directly
    data = MultiDict(mapping=request.form)
    data.add("customer_id", str(customer.id))
    form = forms.CreateHostedDeployment(data)

    status = 200
    message = ''

    analytics_client.track(customer.id, "Submitted Subdomain", {
        'email': markupsafe.escape(login.current_user.email),
        'subdomain': form.subdomain
    })

    if form.validate():
        try:
            hpc.create_deployment(customer, form.subdomain.data)
        except hpc.DeploymentAlreadyExists as e:
            analytics_client.track(customer.id, "Existing Subdomain Error", {
                'email': markupsafe.escape(login.current_user.email),
                'subdomain': form.subdomain
            })
            status = 409
            message = e.msg
    else:
        status = 400
        message = form.subdomain.errors[0]

    return Response(
        response=json.dumps({"message": message}),
        status=status
    )

@blueprint.route("/aerofs-appliance.ova", methods=["GET"])
def download_ova():
    version = appliance.latest_appliance_version()

    if not login.current_user.is_anonymous:
        analytics_client.track(login.current_user.customer_id, 'Downloading OVA', {
            'email': markupsafe.escape(login.current_user.email),
            'version': version
        })

    return redirect(appliance.ova_url(version))

@blueprint.route("/aerofs-appliance.qcow2", methods=["GET"])
def download_qcow():
    version = appliance.latest_appliance_version()

    if not login.current_user.is_anonymous:
        analytics_client.track(login.current_user.customer_id, 'Downloading QCOW', {
            'email': markupsafe.escape(login.current_user.email),
            'version': version
        })

    return redirect(appliance.qcow_url(version))

@blueprint.route("/aerofs-appliance.vhd.gz", methods=["GET"])
def download_vhd():
    version = appliance.latest_appliance_version()

    if not login.current_user.is_anonymous:
        analytics_client.track(login.current_user.customer_id, 'Downloading VHD', {
            'email': markupsafe.escape(login.current_user.email),
            'version': version
        })

    return redirect(appliance.vhd_url(version))

@blueprint.route("/appliance_version", methods=["GET"])
def get_appliance_version():
    version = appliance.latest_appliance_version()
    return Response(version,
            mimetype='text/plain'
            )

# Get the appliance hostname for the given email domain.
# If the domain is not verified, return 404.
# If the customer that has verified that domain does not have an active appliance hostname,
# return 404.
# Otherwise, return the hostname in a json blob:
#
# Example usage:
#   curl localhost:4444/domains/test.co
#
#   { "host": "share.test.co", "domain":"test.co"}
#
@blueprint.route("/domains/<string:mail_domain_val>", methods=["GET"])
def get_appliance_hostname(mail_domain_val):
    domain = models.Domain.query.filter_by(
            mail_domain=mail_domain_val).filter(
            models.Domain.verify_date != None).order_by(
            models.Domain.verify_date.desc()
            ).first_or_404()

    # someone has registered this mail domain... do they have an appliance hostname?
    appliance = domain.customer.appliances.order_by(models.Appliance.modify_date.desc()).first_or_404()
    return Response(json.dumps({'host': appliance.hostname, 'domain':mail_domain_val}),
            mimetype='application/json',
            )


@blueprint.route("/download_latest_license", methods=["GET"])
@login.login_required
def download_latest_license():
    user = login.current_user
    license = user.customer.newest_filled_license()
    if license is None or license.blob is None:
        # (the user shouldn't see this route if they don't have a Filled license)
        abort(404)
    r = Response(license.blob,
            mimetype='application/octet-stream',
            headers={"Content-Disposition": "attachment; filename=aerofs-private-cloud.license"}
            )

    analytics_client.track(login.current_user.customer_id, 'Downloading License File', {
        'email': markupsafe.escape(login.current_user.email),
    })
    return r

@blueprint.route("/start_password_reset", methods=["GET", "POST"])
def start_password_reset():
    form = forms.PasswordResetForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=form.email.data).first()
        if admin and admin.active:
            # Generate a blob hmaced
            email = form.email.data
            s = TimestampSigner(current_app.secret_key)
            token = {"email": email}
            reset_token = base64.urlsafe_b64encode(s.sign(json.dumps(token)))
            reset_link = url_for(".complete_password_reset", reset_token=reset_token, _external=True)
            notifications.send_password_reset_email(admin, reset_link)
        # Note: pretend that password reset worked even if the email given
        # was not known to avoid leaking which emails are registered admins.
        return render_template("password_reset_requested.html")
    return render_template("password_reset.html",
            form=form)

@blueprint.route("/complete_password_reset", methods=["GET", "POST"])
def complete_password_reset():
    reset_blob = request.args.get("reset_token", None)
    if not reset_blob:
        flash(u"No password reset token given.", "error")
        return redirect(url_for(".start_password_reset"))

    try:
        signed_reset_token = base64.urlsafe_b64decode(reset_blob.encode("latin1"))
    except TypeError:
        flash(u"Not a valid password reset token.", "error")
        return redirect(url_for(".start_password_reset"))

    s = TimestampSigner(current_app.secret_key)
    try:
        reset_token = s.unsign(signed_reset_token, max_age=3600)
    except itsdangerous.BadSignature:
        flash(u"Not a valid password reset token.", "error")
        return redirect(url_for(".start_password_reset"))
    except itsdangerous.SignatureExpired:
        flash(u"Password reset token valid but expired.", "error")
        return redirect(url_for(".start_password_reset"))

    if password_reset_helper.has_been_used(reset_blob):
        flash(u"Password reset token valid but has already been used.", "error")
        return redirect(url_for(".start_password_reset"))

    token = json.loads(reset_token)
    token_email = token["email"]

    form = forms.CompleteSignupForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=token_email).first_or_404()
        # Mark as used.
        password_reset_helper.use(reset_blob)
        # Reset the password.
        admin.set_password(form.password.data)
        # Save to DB.
        db.session.add(admin)
        db.session.commit()
        # Destroy all other sessions for this user to prevent replay attack.
        login_helper.logout_user_all_sessions(admin)
        # Log in for this session.
        login_helper.login_user(admin)
        flash(u"Password reset successfully.", 'success')
        return redirect(url_for(".dashboard"))

    return render_template("complete_password_reset.html", email=token_email, form=form)

@blueprint.route("/contact", methods=["GET", "POST"])
@login.login_required
def contact():
    user = login.current_user
    form = forms.ContactForm()

    analytics_client.track(login.current_user.customer_id, 'Visiting Contact Us Page', {
        'email': markupsafe.escape(login.current_user.email),
    })
    if form.validate_on_submit():
        notifications.send_private_cloud_question_email(user.email, form.contact.data, form.subject.data, form.message.data)
        flash(u"Message sent. An AeroFS representative will be in touch shortly.", 'success')
        return redirect(url_for(".dashboard"))

    return render_template("contact.html", form=form)

@blueprint.route("/mobile", methods=["GET"])
@login.login_required
def mobile():
    return render_template("mobile.html",
        mi_android_download="https://s3.amazonaws.com/aerofs.mobile/android/AeroFSAndroidMobileIron.p.apk",
        mi_ios_app_store="https://itunes.apple.com/us/app/aerofs/id933038859"
    )
