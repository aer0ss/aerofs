import base64
import datetime
import os
import json
import urllib

from flask import Blueprint, current_app, render_template, flash, redirect, request, url_for, Response, session
from flask.ext import scrypt, login
import itsdangerous
from itsdangerous import TimestampSigner
import markupsafe

from lizard import analytics_client, db, login_manager, csrf, stripe, filters
from . import appliance, emails, forms, models
import pricing

blueprint = Blueprint('main', __name__, template_folder='templates')

@login_manager.user_loader
def load_user(userid):
    return models.Admin.query.filter_by(email=userid).first()

@blueprint.route('/', methods=['GET'])
@blueprint.route('/index', methods=['GET'])
def index():
    return redirect(url_for(".dashboard"))

@blueprint.route('/login', methods=['GET', 'POST'])
def login_page():
    form = forms.LoginForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=form.email.data).first()
        if not admin:
            # This user doesn't exist.
            flash(u"Email or password is incorrect", 'error')
        elif not scrypt.check_password_hash(form.password.data.encode('utf8'), admin.pw_hash.encode('utf8'), admin.salt.encode('utf8')):
            # The password was wrong.
            flash(u"Email or password is incorrect", 'error')
        else:
            # Successful login.
            login_success = login.login_user(admin, remember=False)
            # TODO: handle inactive users more clearly?  not sure what UX to expose in that case.
            if login_success:
                #flash(u"Login completed for {}:{}".format(form.email.data, form.password.data), 'success')
                pass
            else:
                flash(u"Login failed for {}: probably marked inactive?".format(admin.email), 'error')
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
    login.logout_user()
    flash(u"You have logged out successfully", 'success')
    return redirect(url_for(".index"))

@csrf.exempt
@blueprint.route("/request_signup", methods=["GET", "POST"])
def signup_request_page():
    """
    GET /request_signup - shows form for signing up
    POST /request_signup
    """
    form = forms.SignupForm(csrf_enabled=False)
    email_address = request.args.get("email_address", None)
    if email_address:
        form.email.data = email_address

    if form.validate_on_submit():
        # If email already in Admin table, noop (but return success).
        # We don't want to leak that an account bound to an email exists by
        # returning an error.
        if models.Admin.query.filter_by(email=form.email.data).first():
            return redirect(url_for(".signup_request_done"))
        # If email already in UnboundSignup, just fetch that record.
        record = models.UnboundSignup.query.filter_by(email=form.email.data).first()
        # Otherwise:
        if not record:
            # create random signup code
            # 30 bytes (240 bits) of randomness.
            # could do 32, but using a multiple of 3 means we avoid having
            # base64 padding (==) in urls
            # Note: email verification codes probably shouldn't be typed in
            # manually.  If you're typing links out by hand, you deserve the
            # pain you're in.
            signup_code = base64.urlsafe_b64encode(os.urandom(30))
            # insert code/email pair into UnboundSignup
            record = models.UnboundSignup()
            record.email=form.email.data
            record.signup_code=signup_code
            record.first_name = form.first_name.data
            record.last_name = form.last_name.data
            record.company_name = form.company_name.data
            record.phone_number = form.phone_number.data
            record.job_title = form.job_title.data
            db.session.add(record)
            db.session.commit()
        emails.send_verification_email(form.email.data, record.signup_code)
        flash("https://go.pardot.com/l/32882/2014-03-27/bjxp?first_name={}&last_name={}&email={}&company={}&phone={}&deployment_environment={}"
                .format(urllib.quote(record.first_name.encode('utf-8')),
                        urllib.quote(record.last_name.encode('utf-8')),
                        urllib.quote(record.email.encode('utf-8')),
                        urllib.quote(record.company_name.encode('utf-8')),
                        urllib.quote(record.phone_number.encode('utf-8')),
                        urllib.quote(form.deployment_environment.data.encode('utf-8')))
                ,"pardot")
        return redirect(url_for(".signup_request_done"))
    return render_template("request_signup.html",
            form=form)

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
        return redirect(url_for(".signup_request_page"))

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
        analytics_client.identify(admin.email,
                {
                    'email': markupsafe.escape(admin.email),
                    'firstName': markupsafe.escape(admin.first_name),
                    'lastName': markupsafe.escape(admin.last_name),
                    'company': markupsafe.escape(admin.customer.name),
                    'title': markupsafe.escape(admin.job_title),
                    'phone': markupsafe.escape(admin.phone_number),
                    'Enterprise': 'true',
                })
        analytics_client.track(markupsafe.escape(admin.email), "Signed Up For Private Cloud");

        # Log user in.
        login_success = login.login_user(admin, remember=True)
        if not login_success:
            flash(u"Login failed for {}: probably marked inactive?", "error")

        return redirect(url_for(".index"))
    return render_template("complete_signup.html",
            form=form,
            record=signup)

@blueprint.route("/users/edit", methods=["GET", "POST"])
@login.login_required
def edit_preferences():
    user = login.current_user
    form = forms.PreferencesForm()
    if form.validate_on_submit():
        # Update name
        user.first_name = form.first_name.data
        user.last_name = form.last_name.data
        if len(form.password.data) > 0:
            user.set_password(form.password.data)
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
    # (disabled until we enable it in the form again)
    #form.security_emails.data = user.notify_security
    #form.release_emails.data = user.notify_release
    #form.maintenance_emails.data = user.notify_maintenance
    return render_template("preferences.html",
        form=form,
        user=user
        )

@blueprint.route("/users/invitation", methods=["POST"])
@login.login_required
def invite_to_organization():
    user = login.current_user
    form = forms.InviteForm()
    if form.validate_on_submit():
        customer = user.customer
        email = form.email.data
        # Verify that the email is not already in the Admin table
        if models.Admin.query.filter_by(email=form.email.data).first():
            flash(u'That user is already a member of an organization', 'error')
            return redirect(url_for(".administrators"))
        # Either this user has been invited to the organization already or hasn't.
        # (Since multiple organizations may attempt to invite the same user, we
        # need to filter on both email and org id in the query here.)
        # If she has been invited before (record is not None), we probably want
        # to resend her email invitation because she lost the first one.
        # If she hasn't (record is None), we need to generate a BoundInvite and
        # email it to her.
        record = models.BoundInvite.query.filter_by(email=email, customer_id=customer.id).first()
        if not record:
            invite_code = base64.urlsafe_b64encode(os.urandom(30))
            record = models.BoundInvite()
            record.invite_code = invite_code
            record.email = email
            record.customer_id = customer.id
            db.session.add(record)
            db.session.commit()
        # Send the invite email
        emails.send_invite_email(record.email, customer, record.invite_code)

        flash(u'Invited {} to join {}'.format(email, customer.name), 'success')
        return redirect(url_for('.administrators'))
    flash(u'Sorry, that was an invalid email.', 'error')
    return redirect(url_for('.administrators'))

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
        login_success = login.login_user(admin, remember=True)
        if not login_success:
            flash(u"Login failed for {}: probably marked inactive?", 'error')

        return redirect(url_for(".index"))

    return render_template("accept_invite.html",
            form=form,
            invite=invite,
            customer=customer)

@blueprint.route("/buy", methods=["GET","POST"])
@login.login_required
def buy():
    user = login.current_user

    if 'requested_license_count' in session:
        requested_license_count= session['requested_license_count']
    else:
        flash(u"Please select a license count and try again.", "error")
        return redirect(url_for('.dashboard'))

    newest_license = user.customer.newest_license()

    license_request = models.License()
    license_request.customer = user.customer

    if newest_license.is_trial:
        license_request.set_days_until_expiry(365)
        charge_amount = requested_license_count * pricing.COST_PER_SEAT
        description = "Purchase "  + str(requested_license_count) +" seats"

    else: #pro-rate
        if requested_license_count <= newest_license.seats:
            flash (u"Something went wrong. Your requested license count is lower than your existing license count.", "error")
            return redirect(url_for('.dashboard'))

        license_request.expiry_date = newest_license.expiry_date
        charge_amount = (requested_license_count - newest_license.seats)* pricing.COST_PER_SEAT * \
                        ((license_request.expiry_date.date() - datetime.date.today()).days )/365

        description = "Upgrade from {prev_seats} to {new_seats} seats, pro-rated for {day_delta} days (renewing on {renewal_date})".format(
            prev_seats=newest_license.seats,
            new_seats=requested_license_count,
            day_delta=(license_request.expiry_date.date()-datetime.date.today()).days,
            renewal_date=license_request.expiry_date.strftime('%b %d, %Y')
            )

    if request.method == 'POST':
        # charge the customer
        msg=None
        try:
            customer = user.customer
            if not customer.stripe_customer_id:

                stripe_customer = stripe.Customer.create(
                        email=user.email,
                        card=request.form['stripeToken']
                        )

                customer.stripe_customer_id = stripe_customer.id
                db.session.add(customer)
            else:
                cu = stripe.Customer.retrieve(customer.stripe_customer_id)
                cu.card=request.form['stripeToken']
                cu.save()

            charge = stripe.Charge.create(
                customer=customer.stripe_customer_id,
                amount=charge_amount*100, # in cents
                currency='usd',
                description=description
            )

        except stripe.error.CardError as e:
            # Since it's a decline, stripe.error.CardError will be caught
            body = e.json_body
            err  = body['error']
            msg = err['message']
        except stripe.error.StripeError as e:
            msg = u"An unknown error has occured and your card has not been charged. Please try again later."
            current_app.logger.warn(e)
        except Exception as e:
            msg = u"An internal server error has occured and your card has not been charged. If you continue seeing this error, please contact us at support@aerofs.com."
            current_app.logger.warn(e)
        finally:
            if msg:
                flash(msg, "error")
                return redirect(url_for('.buy'))

        # Charged succesfully, create license request.
        license_request.stripe_charge_id=charge.id
        license_request.state = models.License.LicenseState.PENDING
        license_request.seats = requested_license_count
        license_request.is_trial = False
        license_request.allow_audit = True
        license_request.allow_identity = True
        license_request.allow_mdm = True
        license_request.allow_device_restriction = True
        db.session.add(license_request)

        customer.renewal_seats = license_request.seats

        db.session.add(customer)
        # Commit.
        db.session.commit()

        if newest_license.is_trial:
            flash(u"Successfully bought {} seats.".format(license_request.seats), 'success')
        else:
            flash(u"Your upgrade from {} seats to {} seats is being processed".format(newest_license.seats,requested_license_count), 'success')

        session.pop('requested_license_count', None)
        return redirect(url_for('.dashboard'))

    return render_template("buy.html",
        current_license=newest_license,
        requested_license_count = requested_license_count,
        charge_amount = charge_amount,
        description=description,
        email=user.email
        )

@blueprint.route("/payment_history", methods=["GET"])
@login.login_required
def payment_history():
    user = login.current_user

    if user.customer.stripe_customer_id:
        charges = stripe.Charge.all(customer=user.customer.stripe_customer_id)

        return render_template("payment_history.html",
            charges=charges.data
        )
    else:
        return render_template("payment_history.html",
            charges=None
            )

@blueprint.route("/receipt/<string:id>", methods=["GET"])
@login.login_required
def receipt(id):
    user = login.current_user

    charge = stripe.Charge.retrieve(id)
    if charge.card.customer != user.customer.stripe_customer_id:
            flash(u"This receipt does not exist.", "error")
            return redirect(url_for(".payment_history"))

    return render_template("receipt.html",
        charge=charge
    )

@blueprint.route("/dashboard", methods=["GET", "POST"])
@login.login_required
def dashboard():
    user = login.current_user

    customer = user.customer

    active_license = user.customer.licenses.filter_by(state=models.License.states.FILLED).order_by(
                models.License.is_trial.asc(),
                models.License.expiry_date.desc(),
                models.License.modify_date.desc(),
            ).first()

    newest_license = customer.newest_license()

    form = forms.LicenseCountForm(count=(customer.renewal_seats or newest_license.seats))
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
            active_license=active_license,
            newest_license=newest_license,
            renewal_seats=(customer.renewal_seats or newest_license.seats),
            appliance_version=appliance.latest_appliance_version(),
            mi_android_download="https://s3.amazonaws.com/aerofs.mobile/android/AeroFSAndroidMobileIron.p.apk",
            mi_ios_app_store="https://itunes.apple.com/us/app/aerofs/id933038859"
            )

@blueprint.route("/administrators", methods=["GET"])
@login.login_required
def administrators():
    return render_template("administrators.html",
            user=login.current_user,
            form=forms.InviteForm(),
            )

@blueprint.route("/download_ova", methods=["GET"])
@login.login_required
def download_ova():
    version = appliance.latest_appliance_version()
    # TODO: log that this user has started downloading the OVA.
    return redirect(appliance.ova_url(version))

@blueprint.route("/download_qcow", methods=["GET"])
@login.login_required
def download_qcow():
    version = appliance.latest_appliance_version()
    # TODO: log that this user has started downloading the QCow2.
    return redirect(appliance.qcow_url(version))

@blueprint.route("/download_latest_license", methods=["GET"])
@login.login_required
def download_latest_license():
    user = login.current_user
    # Give the user the license that expires last.  If there exist more than
    # one such license, give them the one that was imported most recently.
    licenses = user.customer.licenses.filter_by(state=models.License.states.FILLED).order_by(
                models.License.expiry_date.desc(),
                models.License.modify_date.desc(),
            )
    license = licenses.first_or_404()
    r = Response(license.blob,
            mimetype='application/octet-stream',
            headers={"Content-Disposition": "attachment; filename=aerofs-private-cloud.license"}
            )
    return r

@blueprint.route("/start_password_reset", methods=["GET", "POST"])
def start_password_reset():
    form = forms.PasswordResetForm()
    if form.validate_on_submit():
        if models.Admin.query.filter_by(email=form.email.data).first():
            # Generate a blob hmaced
            email = form.email.data
            s = TimestampSigner(current_app.secret_key)
            token = {
                    "email": email,
                    }
            reset_token = base64.urlsafe_b64encode(s.sign(json.dumps(token)))
            reset_link = url_for(".complete_password_reset", reset_token=reset_token, _external=True)
            emails.send_password_reset_email(email, reset_link)

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
    except Exception as e:
        print e
        flash(u"Not a valid password reset token.", "error")
        return redirect(url_for(".start_password_reset"))

    s = TimestampSigner(current_app.secret_key)
    try:
        reset_token = s.unsign(signed_reset_token, max_age=3600)
    except itsdangerous.BadSignature as e:
        flash(u"Not a valid password reset token.", "error")
        return redirect(url_for(".start_password_reset"))
    except itsdangerous.SignatureExpired as e:
        flash(u"Password reset token valid but expired.", "error")
        return redirect(url_for(".start_password_reset"))

    token = json.loads(reset_token)
    token_email = token["email"]

    form = forms.CompleteSignupForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=token_email).first_or_404()
        # Reset the password
        admin.set_password(form.password.data)
        # save to DB
        db.session.add(admin)
        db.session.commit()
        # log in the user
        login.login_user(admin, remember=False)
        flash(u"Password reset successfully.", 'success')
        return redirect(url_for(".dashboard"))
    return render_template("complete_password_reset.html", email=token_email, form=form)

@blueprint.route("/contact", methods=["GET", "POST"])
@login.login_required
def contact_us():
    user = login.current_user
    form = forms.ContactForm()
    if form.validate_on_submit():
        emails.send_private_cloud_question_email(user.email, form.message.data)
        flash(u"Message sent successfully.  We'll be in touch.", 'success')
        return redirect(url_for(".dashboard"))
    return render_template("contact_us.html", form=form)
