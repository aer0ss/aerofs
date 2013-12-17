from flask import render_template, flash, redirect, request, url_for
from flask.ext import scrypt, login
from lizard import app, db, emails, forms, login_manager, models

import base64
import os

@login_manager.user_loader
def load_user(userid):
    return models.Admin.query.filter_by(email=userid).first()

# This page is also a temporary stopgap - will be replaced with other pages
# later, but for now, helps with testing
@app.route('/', methods=['GET'])
@app.route('/index', methods=['GET'])
def index():
    user = login.current_user
    if user.is_anonymous():
        return render_template("index.html")
    else:
        return redirect(url_for("dashboard"))

@app.route('/login', methods=['GET', 'POST'])
def login_page():
    form = forms.LoginForm()
    if form.validate_on_submit():
        admin = models.Admin.query.filter_by(email=form.email.data).first()
        if not admin:
            # This user doesn't exist.
            flash(u"Email or password is incorrect")
        elif not scrypt.check_password_hash(form.password.data, admin.pw_hash, admin.salt):
            # The password was wrong.
            flash(u"Email or password is incorrect")
        else:
            # Successful login.
            login_success = login.login_user(admin, remember=False)
            # TODO: handle inactive users more clearly?  not sure what UX to expose in that case.
            if login_success:
                #flash(u"Login completed for {}:{}".format(form.email.data, form.password.data))
                pass
            else:
                flash(u"Login failed for {}: probably marked inactive?".format(admin.email))
            next_url = request.args.get('next') or url_for("index")
            # sanitize next_url to ensure that it's relative.  Avoids user
            # redirection attacks where you log in and then get redirected to an
            # evil site.
            if not next_url.startswith('/'):
                next_url = url_for("index")
            return redirect(next_url)
    return render_template("login.html",
            form=form)

@app.route("/logout")
def logout():
    login.logout_user()
    flash(u"You have logged out successfully")
    return redirect(url_for("index"))

@app.route("/request_signup", methods=["GET", "POST"])
def signup_request_page():
    """
    GET /request_signup - shows form for signing up
    POST /request_signup
    """
    form = forms.SignupForm()
    if form.validate_on_submit():
        # If email already in Admin table, noop (but return success).
        # We don't want to leak that an account bound to an email exists by
        # returning an error.
        if models.Admin.query.filter_by(email=form.email.data).first():
            return redirect(url_for("signup_request_done"))
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
        return redirect(url_for("signup_request_done"))
    return render_template("request_signup.html",
            form=form)

@app.route("/request_signup_done", methods=["GET"])
def signup_request_done():
    return render_template("request_signup_complete.html")

@app.route("/signup", methods=["GET", "POST"])
def signup_completion_page():
    """
    GET /signup?code=<access_code>
    POST /signup?code=<access_code> <form data>
    """
    user_signup_code = request.args.get("signup_code", None)
    if not user_signup_code:
        # return to the "enter your email so we can verify it" page
        return redirect(url_for("signup_request_page"))

    signup = models.UnboundSignup.query.filter_by(signup_code=user_signup_code).first()
    if not signup:
        # This signup code was invalid.
        return redirect(url_for("signup_request_page"))

    form = forms.CompleteSignupForm()
    if form.validate_on_submit():
        # Create a new Customer named `company`
        cust = models.Customer()
        cust.name = signup.company_name
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
        # generate a random salt for this user
        salt = scrypt.generate_random_salt()
        admin.salt = salt
        # scrypt her password with that salt
        admin.pw_hash = scrypt.generate_password_hash(form.password.data, salt)
        db.session.add(admin)

        # Delete any invite codes for that user from the database; they can no
        # longer accept an invitation from a different organization.
        db.session.query(models.BoundInvite).filter(models.BoundInvite.email==signup.email).delete()
        # Delete the signup code from the database, as it is consumed
        db.session.delete(signup)

        # Commit.
        db.session.commit()

        # Log user in.
        login_success = login.login_user(admin, remember=True)
        if not login_success:
            flash(u"Login failed for {}: probably marked inactive?")

        # TODO: also remove the flashed password before deploying to production
        flash(u"Created user {} ({} {} from company {} pw: {}, signup code: {})".format(
            admin.email,
            admin.first_name,
            admin.last_name,
            cust.name,
            form.password.data,
            user_signup_code))
        return redirect(url_for("index"))
    return render_template("complete_signup.html",
            form=form,
            record=signup)

@app.route("/users/edit", methods=["GET", "POST"])
@login.login_required
def edit_preferences():
    user = login.current_user
    form = forms.PreferencesForm()
    if form.validate_on_submit():
        # Update name
        user.first_name = form.first_name.data
        user.last_name = form.last_name.data
        if len(form.password.data) > 0:
            # Always generate a new salt when updating password.
            salt = scrypt.generate_random_salt()
            user.salt = salt
            user.pw_hash = scrypt.generate_password_hash(form.password.data, salt)
        # Update email preferences
        user.notify_security    = form.security_emails.data
        user.notify_release     = form.release_emails.data
        user.notify_maintenance = form.maintenance_emails.data
        # Save to DB
        db.session.add(user)
        db.session.commit()
        flash(u'Saved changes.')
        return redirect(url_for("edit_preferences"))
    form.first_name.data = user.first_name
    form.last_name.data = user.last_name
    form.security_emails.data = user.notify_security
    form.release_emails.data = user.notify_release
    form.maintenance_emails.data = user.notify_maintenance
    return render_template("preferences.html",
        title=u"Edit Preferences",
        form=form,
        user=user
        )

@app.route("/users/invitation", methods=["POST"])
@login.login_required
def invite_to_organization():
    user = login.current_user
    form = forms.InviteForm()
    if form.validate_on_submit():
        customer = user.customer
        email = form.email.data
        # Verify that the email is not already in the Admin table
        if models.Admin.query.filter_by(email=form.email.data).first():
            flash(u'That user is already a member of an organization')
            return redirect(url_for("dashboard"))
        # Either this user has been invited to the organization already or hasn't.
        # If she has been invited before, we probably want to resend her email
        # invitation because she lost the first one.
        # If she hasn't, we need to generate a BoundInvite and email it to her.
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

        flash(u'Invited {} to join {}'.format(email, customer.name))
        return redirect(url_for('dashboard'))
    flash(u'Sorry, that was an invalid email.')
    return redirect(url_for('dashboard'))

@app.route('/users/accept', methods=["GET", "POST"])
def accept_organization_invite():
    user_invite_code = request.args.get("invite_code", None)
    if not user_invite_code:
        # bogus accept code, send user home
        return redirect(url_for("index"))
    invite = models.BoundInvite.query.filter_by(invite_code=user_invite_code).first()
    if not invite:
        # This invite code was invalid.
        return redirect(url_for("index"))
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
        # generate a random salt for this user
        salt = scrypt.generate_random_salt()
        admin.salt = salt
        # scrypt her password with that salt
        admin.pw_hash = scrypt.generate_password_hash(form.password.data, salt)
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
            flash(u"Login failed for {}: probably marked inactive?")

        return redirect(url_for("index"))
    return render_template("accept_invite.html",
            form=form,
            invite=invite,
            customer=customer)

@app.route("/dashboard", methods=["GET"])
@login.login_required
def dashboard():
    form = forms.InviteForm()
    return render_template("dashboard.html",
            user=login.current_user,
            form=form)

# FIXME:
# This is a test page I used to test the behavior of login_required and skip
# dealing with emails.  It should be removed before first release.
@app.route("/magic", methods=["GET"])
@login.login_required
def TODO_DELETE_THIS():
    signups = models.UnboundSignup.query.all()
    return render_template("magic.html",
            signups=signups)
