import datetime

from flask import Blueprint, url_for, render_template, redirect

from lizard import db
from . import forms, models

blueprint = Blueprint('internal', __name__, template_folder='templates')

@blueprint.route("/", methods=["GET"])
def index():
    return "<html><body><pre>Admin page here</pre></body></html>"

@blueprint.route("/licenses", methods=["GET"])
def show_licenses():
    licenses = models.License.query.all()
    return render_template("junk.html",
            licenses=licenses)

@blueprint.route("/internal_request_license", methods=["GET", "POST"])
def internal_request_license():
    #customer_id = request.args.get("customer_id", None)
    form = forms.InternalLicenseRequestForm()
    if form.validate_on_submit():
        # TODO: sanity check all the values!
        l = models.License()
        l.state = models.License.states.PENDING
        l.customer_id = int(form.org_id.data)
        l.seats = int(form.seats.data)
        # The DB keeps datetimes, but the form has only a date.  Augment with time.
        l.expiry_date = datetime.datetime.combine(form.expiry_date.data, datetime.datetime.min.time())
        l.is_trial = form.is_trial.data
        l.allow_audit = form.allow_audit.data
        db.session.add(l)
        db.session.commit()
        return redirect(url_for('.show_licenses'))
    return render_template("request.html",
            form=form)

