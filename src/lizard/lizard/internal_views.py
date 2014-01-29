from StringIO import StringIO
from io import BytesIO
import csv
import datetime
import tarfile

from flask import Blueprint, url_for, render_template, redirect, Response, request, flash

from lizard import db
from . import emails, forms, models

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
    form = forms.InternalLicenseRequestForm()
    if form.validate_on_submit():
        # TODO: sanity check all the values!
        l = models.License()
        l.state = models.License.states.PENDING
        l.customer_id = org_id
        l.seats = int(form.seats.data)
        # The DB keeps datetimes, but the form has only a date.  Augment with time.
        l.expiry_date = datetime.datetime.combine(form.expiry_date.data, datetime.datetime.min.time())
        l.is_trial = form.is_trial.data
        l.allow_audit = form.allow_audit.data
        db.session.add(l)
        db.session.commit()
        return redirect(url_for('.queues'))
    # Set some convenient defaults for renewing a trial license for another 30 seats, ~30 days
    form.seats.data = 30
    form.expiry_date.data = (datetime.datetime.today() + datetime.timedelta(days=32)).date()
    form.is_trial.data = True
    return render_template('customer_actions.html', form=form, customer=customer)

@blueprint.route("/licenses/<int:license_id>", methods=["GET", "POST"])
def license_actions(license_id):
    license = models.License.query.get_or_404(license_id)
    form = forms.InternalLicenseStateForm()
    if form.validate_on_submit():
        license.state = getattr(models.License.states, form.state.data)
        db.session.add(license)
        db.session.commit()
        flash(u"Set license {} to state {}".format(license.id, form.state.data), "success")
        return redirect(url_for(".queues"))
    form.state.data = models.License.states.states[license.state]
    return render_template("license_actions.html", form=form, license=license)

@blueprint.route("/all_customers", methods=["GET"])
def all_customers():
    customers = models.Customer.query.all()
    return render_template("all_customers.html", customers=customers)

@blueprint.route("/download_csv", methods=["GET"])
def download_license_request_csv():
    to_add = models.License.query.filter_by(state=models.License.states.PENDING).all()
    s = StringIO()
    c = csv.DictWriter(s, ["ID", "Company", "Seats", "Expiration", "Trial", "Audit"])
    c.writeheader()
    for row in to_add:
        c.writerow({
            "ID":row.customer_id,
            "Company":row.customer.name,
            "Seats":row.seats,
            "Expiration": row.expiry_date.strftime("%Y-%m-%d"),
            "Trial":row.is_trial,
            "Audit":row.allow_audit,
            })
    return Response(s.getvalue(),
                mimetype='text/csv; charset=utf-8',
                headers={"Content-Disposition": "attachment; filename=license_requests.csv"}
                )

@blueprint.route("/upload_bundle", methods=["GET", "POST"])
def upload_bundle():
    print request.files
    form = forms.InternalLicenseBundleUploadForm()
    if form.validate_on_submit():
        blob = form.license_bundle.data
        # Read tarball
        tarball = tarfile.open(fileobj=blob.stream)
        tarball.list()
        license_index_flo = tarball.extractfile("license-index")
        index_csv = csv.DictReader(license_index_flo)
        # The construction here gives safe incremental progress.  You can just
        # keep uploading the same license bundle and it'll ignore the files
        # that are already in the DB.
        for row in index_csv:
            print row
            # extract ID, seats, trial, audit, expiry date, issue date, filename
            customer_id = row["ID"]
            seats = row["Seats"]
            expiry_date = datetime.datetime.strptime(row["Expiration"], "%Y-%m-%d")
            issue_date = row["Issue Date"]
            is_trial = row["Trial"] == "True"
            allow_audit = row["Allow Audit"] == "True"
            filename = row["Filename"]
            # Look for a matching License in state PENDING
            license_request = models.License.query.filter_by(customer_id=customer_id,
                    state=models.License.states.PENDING,
                    seats=seats,
                    expiry_date=expiry_date,
                    is_trial=is_trial,
                    allow_audit=allow_audit,
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
                db.session.commit()
                # email all admins in org
                for admin in license_request.customer.admins:
                    print "emailing", admin, "about new license"
                    emails.send_license_available_email(admin.email, license_request.customer)
            else:
                print "Got a license descriptor without no outstanding license request. Discarding."
    return render_template('upload_bundle.html', form=form)
