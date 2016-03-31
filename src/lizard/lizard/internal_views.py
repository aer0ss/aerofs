import datetime
import stripe
import tarfile
import requests
from StringIO import StringIO
from hpc_config import reboot, new_authed_session
from aerofs_licensing import unicodecsv
from lizard import db, csrf, appliance, notifications, forms, models, hpc
from flask import Blueprint, current_app, url_for, render_template, redirect, Response, request, \
    flash, jsonify

blueprint = Blueprint('internal', __name__, template_folder='templates')

MONITORING_PORT = 5000
AUTOSCALING_NUMBER_NEW_INSTANCES = 1

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
                stripe_customer.subscriptions.retrieve(form.stripe_subscription_id.data)
            except stripe.InvalidRequestError, e:
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
    form.expiry_date.data = datetime.datetime.today() + datetime.timedelta(days=30)
    form.is_trial.data = False
    form.allow_audit.data = True
    form.allow_identity.data = True
    form.allow_mdm.data = True
    form.allow_device_restriction.data = True
    charge_data = charges.data if charges else None

    hpc_form = forms.CreateHostedDeployment()
    hpc_form.customer_id.data = org_id

    return render_template('customer_actions.html', form=form, customer=customer, charges=charge_data,
                           hpc_form=hpc_form)

@blueprint.route("/licenses/<int:license_id>", methods=["GET", "POST"])
def license_actions(license_id):
    license = models.License.query.get_or_404(license_id)
    form = forms.InternalLicenseStateForm(invoice_id=license.invoice_id, stripe_subscription_id=license.stripe_subscription_id)

    if form.validate_on_submit():
        if license.customer.stripe_customer_id and form.stripe_subscription_id.data:
            try:
                stripe_customer = stripe.Customer.retrieve(license.customer.stripe_customer_id)
                subscription = stripe_customer.subscriptions.retrieve(form.stripe_subscription_id.data)
            except stripe.InvalidRequestError, e:
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


# Record the hostname of the appliance for the given customer.
# We can't validate this hostname; it may be VPN only, or an internal IP.
#
# Example usage:
#   curl -XPOST localhost:4444/customers/1/host -d '{"hostname":"share.test.co"}'
#
@csrf.exempt
@blueprint.route("/customers/<int:customer_id>/host", methods=["POST"])
def set_appliance_hostname(customer_id):
    body = request.get_json(force=True)
    hostname = body["hostname"]
    current_app.logger.info("Register: host %s for customer %d", hostname, customer_id)

    customer = models.Customer.query.get(customer_id)
    if customer is None:
        return Response('No such customer', 404)

    appliance = models.Appliance()
    appliance.customer = customer
    appliance.hostname = hostname
    db.session.add(appliance)
    db.session.commit()

    return Response('', 204)



# Create a mail domain record and associate it with a customer. This is not a regex!
# The mail domain cannot be queried by public users until it has been verified.
#
# Example usage:
#   curl -XPUT localhost:4444/customers/1/domains/test.co
#
@csrf.exempt
@blueprint.route("/customers/<int:customer_id>/domains/<string:domain_val>", methods=["PUT"])
def register_domain_for_customer(customer_id, domain_val):
    current_app.logger.info("Register: domain %s for customer %d", domain_val, customer_id)

    customer = models.Customer.query.get(customer_id)
    if customer is None:
        return Response('No such customer', 404)

    d = models.Domain()
    d.customer = customer
    d.mail_domain = domain_val
    db.session.add(d)
    db.session.commit()

    return Response('', 204)


# Mark the given mail domain as "verified".
# Actually verifying the mail domain is a handwave. For now we do this manually.
#
# Example usage:
#   curl -XPUT localhost:4444/customers/1/domains/test.co/verify
#
@csrf.exempt
@blueprint.route("/customers/<int:customer_id>/domains/<string:domain_val>/verify", methods=["PUT"])
def verify_domain_for_customer(customer_id, domain_val):
    current_app.logger.info("Verified: domain %s for customer %d", domain_val, customer_id)

    customer = models.Customer.query.get(customer_id)
    if customer is None:
        return Response('No such customer', 404)

    domain = customer.domains.filter_by(mail_domain=domain_val).first_or_404()
    domain.verify_date = datetime.datetime.now()
    db.session.commit()

    return Response('', 204)



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
        search_terms_sanitized = request.args.get('search_terms', None).encode('utf-8')
        # InnoDB tables don't support full text search on the "@" character, because it is reserved
        # so we replace the @ with a space.
        search_terms_sanitized = search_terms_sanitized.replace("@"," ")
        # Add the star postfix for prefix matching.
        search_terms_sanitized = ' '.join([t + '*' for t in search_terms_sanitized.split(' ')])

        query = models.Customer.query.filter(
            models.Customer.name.match(search_terms_sanitized) |
            models.Customer.admins.any(models.Admin.email.match(search_terms_sanitized)))
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
    # This is possibly inefficient because group by is hard to understand/map to sqlalchemy and this
    # is internal anyway.
    # Pick out licenses that are still valid and for paying accounts.
    licenses = models.License.query.filter(models.License.state == models.License.LicenseState.FILLED).\
                                    filter(models.License.expiry_date > now).\
                                    filter(models.License.is_trial == False)
    # Collect the matching customers (those folks with currently-valid licenses). Since a customer
    # may have multiple currently-valid licenses, (say, bought one year at 200 seats, then upgraded
    # to 400 seats later that year), uniquify the customer list.
    customers = list(set([l.customer for l in licenses]))
    rows = []
    total_paid_seats = 0
    for c in customers:
        # Skip aerofs.com emails.
        aerofs_internal = False
        for a in c.admins:
            if a.email.endswith("@aerofs.com"):
                aerofs_internal = True
                break
        if aerofs_internal: continue
        # For each customer, we're only really interested in the most-recently-issued license
        l = models.License.query.\
                filter(models.License.customer_id == c.id).\
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
        # Read tarball.
        tarball = tarfile.open(fileobj=blob.stream)
        tarball.list()
        license_index_flo = tarball.extractfile("license-index")
        index_csv = unicodecsv.UnicodeDictReader(license_index_flo)
        just_imported = []
        # The construction here gives safe incremental progress. You can just keep uploading the
        # same license bundle and it'll ignore the files that are already in the DB.
        for row in index_csv:
            print row
            # Extract ID, seats, trial, audit, expiry date, issue date, filename.
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
            # Look for a matching License in state PENDING.
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
                # Extract license file from tarball.
                license_file_flo = tarball.extractfile(filename)
                license_file_bytes = license_file_flo.read()
                # Update License: set license file blob, state=FILLED.
                license_request.blob = license_file_bytes
                license_request.state = models.License.states.FILLED
                # Commit.
                db.session.add(license_request)
                just_imported.append(row)
                # Email all admins in org.
                for admin in license_request.customer.admins:
                    print "emailing", admin, "about new license"
                    notifications.send_license_available_email(admin)

                    # log new license in salesforce/pardot
                    pardot_params = {
                        'email' : admin.email.encode('utf-8'),
                        'aerofs_license_type__c' : "Teams" if license_request.is_trial else "Business",
                        'aerofs_number_of_users__c' : license_request.seats,
                        'aerofs_product_expiration_date__c' : license_request.expiry_date,
                        'aerofs_license_created_date__c' : license_request.create_date
                    }
                    requests.get("https://go.pardot.com/l/32882/2016-01-25/4pp6mq", params=pardot_params)

                # Prefer committing after sending the emails.In the case of a failure, admins listed
                # before the one triggering the failure will get duplicate emails, but if you commit
                # before sending the mails and there's an email failure, that message to the user
                # will be lost and they'll hear nothing from us.
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
        elif not appliance.vhd_present_for(version):
            flash(u'Missing file for release: {}'.format(appliance.vhd_url(version)), 'error')
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
            vhd_url=appliance.vhd_url(current_ver),
            )

@blueprint.route("/customers/<int:org_id>/delete_customer_account", methods=["POST"])
def delete_customer_account(org_id):

    customer = models.Customer.query.get_or_404(org_id)

    for admin in customer.admins:
        if admin.customer_id == org_id:
            db.session.delete(admin)

    for license in customer.licenses:
        if license.customer_id == org_id:
            db.session.delete(license)

    db.session.delete(customer)
    db.session.commit()
    flash(u'Customer account deleted.', 'success')

    return redirect(url_for("internal.all_accounts"))

@blueprint.route("/hpc_deployments", methods=["GET", "POST"])
def hpc_deployments():
    form = forms.CreateHostedDeployment()

    if request.method == 'POST':
        if form.validate_on_submit():
            customer = models.Customer.query.get(form.customer_id.data)
            if customer is None:
                flash("No such customer", 'error')
            else:
                try:
                    hpc.create_deployment(customer, form.subdomain.data)
                except hpc.DeploymentAlreadyExists:
                    flash("A deployment with this subdomain already exists", 'error')
        else:
            flash("Only letters, numbers and dashes are allowed for the subdomain,"
                  " and it can't start or end with a dash.", 'error')

    # TODO (GS): filter & order query
    deployments = models.HPCDeployment.query.all()

    down_deployments = [deployment for deployment in deployments if
                        deployment.setup_status == models.HPCDeployment.status.DOWN]
    up_deployments = [deployment for deployment in deployments if
                      deployment.setup_status == models.HPCDeployment.status.UP]
    progress_deployments = [deployment for deployment in deployments if
                            deployment.setup_status == models.HPCDeployment.status.IN_PROGRESS]

    return render_template('hpc_deployments.html',
                           up_deployments=up_deployments,
                           down_deployments=down_deployments,
                           progress_deployments=progress_deployments)


@blueprint.route("/hpc_recreate_deployment/<string:subdomain>", methods=["POST"])
def hpc_recreate_deployment(subdomain):
    # Deleting the deployment and starting it again
    deployment = models.HPCDeployment.query.get_or_404(subdomain)
    if deployment.setup_status == models.HPCDeployment.status.DOWN:
        customer = deployment.customer
        deleted = hpc.delete_deployment(deployment)
        if not deleted:
            return Response('Failed ', status=500)
        # Give 30 seconds for container/deployment deletion.
        hpc.create_deployment(customer, subdomain, 30)
    else:
        Response('The deployment you are trying to delete is not down', status=500)

    return Response(status=200)

@blueprint.route("/hpc_deployments/<string:subdomain>", methods=["GET"])
def hpc_deployment(subdomain):
    deployment = models.HPCDeployment.query.get_or_404(subdomain)
    return render_template('hpc_deployment.html', deployment=deployment)


@blueprint.route("/hpc_deployments/<string:subdomain>", methods=["DELETE"])
def hpc_deployment_delete(subdomain):
    deployment = models.HPCDeployment.query.get_or_404(subdomain)
    hpc.delete_deployment(deployment)
    return Response('ok', 200)


@blueprint.route("/hpc_extend_license/<string:subdomain>", methods=["POST"])
def extend_license(subdomain):
    duration = int(request.form['newDuration'])

    # Update the expiry date in the license table
    deployment = models.HPCDeployment.query.get_or_404(subdomain)
    deployment.set_days_until_expiry(duration)

    # Generate a license request.
    license = deployment.customer.newest_filled_license()
    license.state = models.License.states.PENDING

    db.session.commit()

    # The license signing script will automatically download the license
    # requests and sign them 10 times per second.

    # Rebooting the appliance
    # Wait a couple of seconds to make sure the license has been granted then
    # reboot the appliance with the new license.
    reboot.si(subdomain).apply_async(countdown=10)
    return jsonify({'status': duration})


@blueprint.route("/hpc_servers", methods=["GET", "POST"])
def hpc_servers():
    form = forms.AddHPCServer()

    if form.validate_on_submit():
        launch_server(form.server_name.data)

    servers = models.HPCServer.query.all()
    server_sys_stats = {}

    for server in servers:
        # Getting the system statistics of the server
        try:
            server_sys_stats[server.id] = _get_server_sys_stats(server.docker_url)
        except requests.ConnectionError:
            server_sys_stats[server.id] = None

    return render_template('hpc_servers.html',
                           servers=servers,
                           form=form,
                           stats=server_sys_stats)


@blueprint.route("/hpc_servers/<int:server_id>", methods=["DELETE"])
def hpc_server_delete(server_id):
    server = models.HPCServer.query.get_or_404(server_id)
    hpc.delete_server(server)
    return Response('ok', 200)


# This function returns a 200 if all hpc deployments are up, else it
# returns a 500 if any service in any deployment is down.
# If a subdomain name is given in the query parameters, we just return the
# status of that particular deployment
@blueprint.route("/hpc_deployments_status", methods=['GET'])
def hpc_deployments_status():
    if request.args:
        subdomain_status = _subdomain_deployment_status(request.args.get('subdomain'))
        return jsonify(subdomain_status)

    servers = models.HPCServer.query.all()
    deployments_status_by_server = {}
    # if one of the deployment is down, the status_code will be set to 500
    status_code = 200

    for server in servers:
        problematic_deployments = _get_problematic_deployments(server.id)
        if problematic_deployments:
            deployments_status_by_server[server.id] = problematic_deployments
            status_code = 500
        else:
            deployments_status_by_server[server.id] = []

    response = jsonify(deployments_status_by_server)
    response.status_code = status_code
    return response


# This function returns a 200 if all hpc servers's system stats(RAM,CPU, disk)
# are under their assigned thresholds(via pagerduty).
# Currently, it is only called by hpc_check_sys_stats in pagerduty
@blueprint.route("/hpc_server_sys_stats", methods=['GET'])
def hpc_server_sys_stats():
    # Getting the different threshold from the request
    threshold = {}
    threshold['cpu_usage_percent'] = float(request.args.get('cpu_threshold'))
    threshold['mem_usage_percent'] = float(request.args.get('mem_threshold'))
    threshold['disk_usage_percent'] = float(request.args.get('disk_threshold'))

    servers = models.HPCServer.query.all()
    server_infos = {}

    for server in servers:
        server_sys_stats = _get_server_sys_stats(server.docker_url)

        # If any usage is higher than the threshold, we store it in a list and
        # return a 500 Response at the end
        for stats in server_sys_stats:
            if server_sys_stats[stats] > threshold[stats]:
                server_infos.setdefault(server.id, []).append(stats)

    if server_infos:
        response = jsonify(server_infos)
        response.status_code = 500
        return response
    else:
        return Response('Ok', 200)


@blueprint.route("/launch_server", methods=['POST'])
def launch_server(server_name=''):
    if not server_name:
        last_server_id = models.HPCServer.query.order_by(-models.HPCServer.id).first().id
        server_name = 'hpc-server-{}'.format(last_server_id+1)
    for i in range(AUTOSCALING_NUMBER_NEW_INSTANCES):
        hpc.launch_server.si(server_name).apply_async()

    return Response(200)


# This function gives the status of a subdomain
def _subdomain_deployment_status(subdomain):
    # Getting the status of the each part of a deployment
    try:
        session = new_authed_session(subdomain)
        r = session.get('/admin/json-status')
        subdomain_status = r.json()
    except requests.exceptions.ConnectionError:
        message = 'Connection error: Max retries exceeded with url: /admin/login'
        subdomain_status = {'statuses': [{'is_healthy': False,
                                          'message': message}]}
    return subdomain_status


# This function returns a list that is empty if all deployments of the server
# are up. If not, the list contains the name of the deployments that are down.
def _get_problematic_deployments(server_id):
    # we get the status only of the deployments that are in the server which id
    # is given as a parameter
    deployments_list = models.HPCDeployment.query.filter(
        models.HPCDeployment.server_id == server_id,
        models.HPCDeployment.setup_status != models.HPCDeployment.status.IN_PROGRESS).all()

    problematic_deployments = []

    for deployment in deployments_list:
        # We get the status of all the container of each deployment
        statuses = _subdomain_deployment_status(deployment.subdomain)

        # If one of the container is down, we add it to the list of down deployments
        for status in statuses['statuses']:
            if not status['is_healthy']:
                problematic_deployments.append(deployment.subdomain)

    return problematic_deployments


def _internal_ip(docker_url):
    # Docker url is of the form https://<ip-addr>:port>.
    # Use rfind to get index of 2nd ":" in docker url and extract away port
    # to get internal ip.
    end = docker_url.rfind(":")
    return docker_url[len("https://"):end]


def _get_server_sys_stats(docker_url):
    url = 'http://{}:{}'.format(_internal_ip(docker_url), MONITORING_PORT)
    r = requests.get(url)
    return r.json()
