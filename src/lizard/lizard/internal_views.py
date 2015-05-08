from StringIO import StringIO
import datetime
import tarfile

from flask import Blueprint, url_for, render_template, redirect, Response, request, flash

from aerofs_licensing import unicodecsv
from lizard import db, csrf
from . import appliance, notifications, forms, models

import stripe

blueprint = Blueprint('internal', __name__, template_folder='templates')

@blueprint.route("/", methods=["GET"])
def index():
    return render_template('internal_index.html')

@blueprint.route("/queues", methods=["GET"])
def queues():
    pending = models.License.query.filter_by(state=models.License.states.PENDING).all()
    on_hold = models.License.query.filter_by(state=models.License.states.ON_HOLD).all()
    ignored = models.License.query.filter_by(state=models.License.states.IGNORED).all()
    return render_template("queues.html",
            pending=pending,
            on_hold=on_hold,
            ignored=ignored,
            )

@blueprint.route("/customers/<int:org_id>", methods=["GET", "POST"])
def customer_actions(org_id):
    customer = models.Customer.query.get_or_404(org_id)

    charges = None
    if customer.stripe_customer_id:
        charges = stripe.Charge.all(customer=customer.stripe_customer_id)

    form = forms.InternalLicenseRequestForm()
    if form.validate_on_submit():
        # TODO: sanity check all the values!
        if customer.stripe_customer_id and form.stripe_subscription_id.data:
            try:
                stripe_customer = stripe.Customer.retrieve(customer.stripe_customer_id)
                subscription = stripe_customer.subscriptions.retrieve(form.stripe_subscription_id.data)
            except stripe.error.InvalidRequestError, e:
                body = e.json_body
                err = body['error']
                flash (err['message'], "error")
                return redirect(url_for('.customer_actions',org_id=org_id))

        l = models.License()
        l.state = models.License.states.PENDING
        l.customer_id = org_id
        l.seats = int(form.seats.data)
        # The DB keeps datetimes, but the form has only a date.  Augment with time.
        l.expiry_date = datetime.datetime.combine(form.expiry_date.data, datetime.datetime.min.time())
        l.is_trial = form.is_trial.data
        l.allow_audit = form.allow_audit.data
        l.allow_identity = form.allow_identity.data
        l.allow_mdm = form.allow_mdm.data
        l.allow_device_restriction = form.allow_device_restriction.data
        l.stripe_subscription_id = form.stripe_subscription_id.data
        l.invoice_id = form.manual_invoice.data
        db.session.add(l)
        db.session.commit()
        return redirect(url_for('.queues'))
    # Set some convenient defaults for paid license for another 35 seats, ~1 year
    form.seats.data = 35
    form.expiry_date.data = datetime.datetime.today()
    form.is_trial.data = False
    form.allow_audit.data = True
    form.allow_identity.data = True
    form.allow_mdm.data = True
    form.allow_device_restriction.data = True
    charge_data = charges.data if charges else None
    return render_template('customer_actions.html', form=form, customer=customer, charges=charge_data)

@blueprint.route("/licenses/<int:license_id>", methods=["GET", "POST"])
def license_actions(license_id):
    license = models.License.query.get_or_404(license_id)
    form = forms.InternalLicenseStateForm(invoice_id=license.invoice_id, stripe_subscription_id=license.stripe_subscription_id)

    if form.validate_on_submit():
        if license.customer.stripe_customer_id and form.stripe_subscription_id.data:
            try:
                stripe_customer = stripe.Customer.retrieve(license.customer.stripe_customer_id)
                subscription = stripe_customer.subscriptions.retrieve(form.stripe_subscription_id.data)
            except stripe.error.InvalidRequestError, e:
                body = e.json_body
                err = body['error']
                flash (err['message'], "error")
                return redirect(url_for('.license_actions',license_id=license_id))
        if form.state.data != "None": #Flask is being weird and won't let me compare to regular "is not None"
            license.state = getattr(models.License.states, form.state.data)
        license.stripe_subscription_id = form.stripe_subscription_id.data
        license.invoice_id = form.invoice_id.data
        db.session.add(license)
        db.session.commit()
        flash(u"Updated license {}".format(license.id), "success")
        return redirect(url_for(".queues"))
    form.state.data = models.License.states.states[license.state]
    return render_template("license_actions.html", form=form, license=license)

@blueprint.route("/licenses/<int:license_id>/download", methods=["GET"])
def license_download(license_id):
    license = models.License.query.get_or_404(license_id)
    r = Response(license.blob,
                mimetype='application/octet-stream',
                headers={"Content-Disposition": "attachment; filename=aerofs-private-cloud.license"}
                )
    return r

PAGE_SIZE = 10

@blueprint.route("/all_accounts", methods=["GET"])
def all_accounts():
    # URL Parameters.
    search_terms = request.args.get('search_terms', None)
    page = int(request.args.get('page', 1))
    # Form.
    form = forms.AllAccountsSearchForm()
    form.search_terms.data = search_terms
    # Search.
    if search_terms:
        query = models.Customer.query.filter(
            models.Customer.name.ilike("%" + search_terms + "%") |
            models.Customer.admins.any(models.Admin.email.ilike("%" + search_terms + "%")))
    else:
        query = models.Customer.query
    # Ordering.
    query = query.order_by(models.Customer.name.asc())
    # Results.
    accounts = query.limit(PAGE_SIZE).offset((page - 1) * PAGE_SIZE)
    total_pages = (query.count() / PAGE_SIZE) + 1
    # Rendering.
    return render_template("all_accounts.html",
            form=form,
            accounts=accounts,
            page=page,
            total_pages=total_pages,
            request_args={'search_terms': search_terms})

@blueprint.route("/paying_accounts", methods=["GET"])
def paying_accounts():
    now = datetime.datetime.today()
    # This is possibly inefficient because group by is hard to understand/map
    # to sqlalchemy and this is internal anyway
    # Pick out licenses that are still valid and for paying accounts
    licenses = models.License.query.filter(models.License.state == models.License.LicenseState.FILLED).\
                                    filter(models.License.expiry_date > now).\
                                    filter(models.License.is_trial == False)
    # Collect the matching customers (those folks with currently-valid
    # licenses).  Since a customer may have multiple currently-valid licenses,
    # (say, bought one year at 200 seats, then upgraded to 400 seats later that
    # year), uniquify the customer list.
    customers = list(set([l.customer for l in licenses]))
    rows = []
    total_paid_seats = 0
    for c in customers:
        # For each customer, we're only really interested in the most-recently-issued license
        l = models.License.query.\
                filter(models.License.customer_id==c.id).\
                order_by(models.License.create_date.desc()).\
                first()
        # To handle the case where the customer paid us at one point but no longer does (because of
        # downgrades or grandfathering), do not append if the latest license is not paid.
        if l.is_trial: continue
        rows.append( {"customer": c, "license": l} )
        total_paid_seats += l.seats
    rows = sorted(rows, key=lambda row: row["license"].seats, reverse=True)
    return render_template("paying_accounts.html", rows=rows, total_paid_seats=total_paid_seats)

@blueprint.route("/download_csv", methods=["GET"])
def download_license_request_csv():
    to_add = models.License.query.filter_by(state=models.License.states.PENDING).all()
    s = StringIO()
    c = unicodecsv.UnicodeDictWriter(s, ["ID", "Company", "Seats", "Expiry Date", "Trial", "Allow Audit", "Allow Identity", "Allow MDM", "Allow Device Restriction"])
    c.writeheader()
    for row in to_add:
        c.writerow({
            "ID": unicode(row.customer_id),
            "Company": row.customer.name,
            "Seats": unicode(row.seats),
            "Expiry Date": row.expiry_date.strftime("%Y-%m-%d"),
            "Trial": unicode(row.is_trial),
            "Allow Audit": unicode(row.allow_audit),
            "Allow Identity": unicode(row.allow_identity),
            "Allow MDM": unicode(row.allow_mdm),
            "Allow Device Restriction": unicode(row.allow_device_restriction)
            })
    return Response(s.getvalue(),
                mimetype='text/csv; charset=utf-8',
                headers={"Content-Disposition": "attachment; filename=license_requests.csv"}
                )

@csrf.exempt
@blueprint.route("/upload_bundle", methods=["GET", "POST"])
def upload_bundle():
    print request.files
    form = forms.InternalLicenseBundleUploadForm(csrf_enabled=False)
    if form.validate_on_submit():
        blob = form.license_bundle.data
        # Read tarball
        tarball = tarfile.open(fileobj=blob.stream)
        tarball.list()
        license_index_flo = tarball.extractfile("license-index")
        index_csv = unicodecsv.UnicodeDictReader(license_index_flo)
        just_imported = []
        # The construction here gives safe incremental progress.  You can just
        # keep uploading the same license bundle and it'll ignore the files
        # that are already in the DB.
        for row in index_csv:
            print row
            # extract ID, seats, trial, audit, expiry date, issue date, filename
            customer_id = row["ID"]
            seats = row["Seats"]
            expiry_date = datetime.datetime.strptime(row["Expiry Date"], "%Y-%m-%d")
            #issue_date = row["Issue Date"] # Present, but unused
            is_trial = row["Trial"].lower() == "true"
            allow_audit = row["Allow Audit"].lower() == "true"
            allow_identity = row["Allow Identity"].lower() == "true"
            allow_mdm = row["Allow MDM"].lower() == "true"
            allow_device_restriction = row["Allow Device Restriction"].lower() == "true"
            filename = row["Filename"]
            # Look for a matching License in state PENDING
            license_request = models.License.query.filter_by(customer_id=customer_id,
                    state=models.License.states.PENDING,
                    seats=seats,
                    expiry_date=expiry_date,
                    is_trial=is_trial,
                    allow_audit=allow_audit,
                    allow_identity=allow_identity,
                    allow_mdm=allow_mdm,
                    allow_device_restriction=allow_device_restriction
                    ).first()
            if license_request is not None:
                # Extract license file from tarball
                license_file_flo = tarball.extractfile(filename)
                license_file_bytes = license_file_flo.read()
                # Update License: set license file blob, state=FILLED
                license_request.blob = license_file_bytes
                license_request.state = models.License.states.FILLED
                # commit
                db.session.add(license_request)
                just_imported.append(row)
                # email all admins in org
                for admin in license_request.customer.admins:
                    print "emailing", admin, "about new license"
                    notifications.send_license_available_email(admin, license_request.customer)
                # Prefer committing after sending the emails.  In the case of a
                # failure, admins listed before the one triggering the failure
                # will get duplicate emails, but if you commit before sending
                # the mails and there's an email failure, that message to the
                # user will be lost and they'll hear nothing from us.
                db.session.commit()
            else:
                print "Got a license descriptor without no outstanding license request. Discarding."
        flash(u"Imported {} new licenses.".format(len(just_imported)), "success")
        return redirect(url_for(".queues"))
    return render_template('upload_bundle.html', form=form)

@blueprint.route("/release", methods=["GET", "POST"])
def release():
    form = forms.ReleaseForm()
    current_ver = appliance.latest_appliance_version()
    if form.validate_on_submit():
        version = form.release_version.data
        print "would attempt to release", version
        # Check that the two files we expect to be in the bucket are in the bucket
        if not appliance.ova_present_for(version):
            flash(u'Missing file for release: {}'.format(appliance.ova_url(version)), 'error')
        elif not appliance.qcow_present_for(version):
            flash(u'Missing file for release: {}'.format(appliance.qcow_url(version)), 'error')
        else:
            appliance.set_public_version(version)
            flash(u'Released version {}'.format(version), 'success')
            notifications.send_internal_appliance_release_notification(version)
            return redirect(url_for('.release'))
    return render_template("release_version.html",
            form=form,
            current_version=current_ver,
            ova_url=appliance.ova_url(current_ver),
            qcow_url=appliance.qcow_url(current_ver),
            )
