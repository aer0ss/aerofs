import datetime
import sys
import os

from flask.ext import scrypt
from aerofs_licensing import unicodecsv

from lizard import create_app, db, models

app = create_app()

def import_licenses_from(license_data_dir, id_user_map):
    if not os.path.isdir(license_data_dir):
        raise IOError("Expected {} to be a directory but it is not".format(license_data_dir))
    license_index_file = os.path.join(license_data_dir, "index.csv")
    with open(license_index_file) as license_index:
        license_index_rows = unicodecsv.UnicodeDictReader(license_index)
        n = 0
        for row in license_index_rows:
            # 1) Ensure Customer with that ID exists.
            customer_id = int(row["ID"])
            cust = models.Customer.query.get(customer_id)
            if not cust:
                cust = models.Customer()
                cust.id = customer_id
                cust.name = row["Company"]
                cust.active = True
                cust.accepted_license = False # well, technically, they haven't!  We don't seem to use this anywhere though.
                print "\tImporting org {}".format(customer_id)
                db.session.add(cust)
            else:
                print "\tAlready imported org {}".format(customer_id)
            # 2) Ensure that we have create all known Admins accounts
            # strong checks: verify email not empty, figure out appropriate values for the rest of the dataset
            if str(customer_id) not in id_user_map:
                raise ValueError("NEEDINFO Expected contact info for {} but found none".format(customer_id))
            for u in id_user_map[str(customer_id)]:
                first = u["First"]
                last = u["Last"]
                email = u["Email"]
                if models.Admin.query.filter_by(email=email).first():
                    print "\tAlready imported {}".format(email)
                    continue
                if len(email) == 0:
                    raise ValueError("Expected nonempty email address, got {} {} {}".format(first, last, email))
                admin = models.Admin()
                admin.customer = cust
                admin.email = email
                admin.first_name = first
                admin.last_name = last
                admin.phone_number = ""
                admin.job_title = ""
                admin.active = True
                admin.notify_security = False
                admin.notify_release = False
                admin.notify_maintenance = False
                # Set the default password to something unique, random, and ridiculously long.
                admin.set_password(scrypt.generate_random_salt())
                # Add this change to the session
                print "\timporting user {} {} ({})".format(first, last, email)
                db.session.add(admin)
            # 3) Import the license itself
            seats = int(row["Seats"])
            expiry_date = datetime.datetime.strptime(row["Expiry Date"], "%Y-%m-%d")
            is_trial = (row["Trial"] == "true")
            license = models.License.query.filter_by(state=models.License.LicenseState.FILLED,
                    customer=cust,
                    seats=seats,
                    expiry_date=expiry_date,
                    is_trial=is_trial,
                    ).first()
            if not license:
                license = models.License()
                license.state = models.License.LicenseState.FILLED
                license.customer = cust
                license.seats = seats
                license.expiry_date = expiry_date
                license.is_trial = is_trial
                license.allow_audit = False
                # load license data from folder
                license_file = os.path.join(license_data_dir, "issued_licenses", row["ID"], row["Issue Date"] + ".license")
                with open(license_file) as l:
                    blob = l.read()
                license.blob = blob
                print "\timporting license (customer {}, {} users, expires {})".format(customer_id, seats, expiry_date.isoformat())
                db.session.add(license)
            else:
                print "\tAlready have license for (customer {}, {} users, expires {})".format(customer_id, seats, expiry_date.isoformat())
            # 4) Commit this row (incremental progress)
            n = n + 1
            print "[{:03d}] Processed {}".format(n, cust.name)
            db.session.commit()

def load_id_user_maps(license_requests_dir):
    m = {}
    for fname in os.listdir(license_requests_dir):
        if not fname.endswith(".csv"):
            continue
        fpath = os.path.join(license_requests_dir, fname)
        with open(fpath) as f:
            c = unicodecsv.UnicodeDictReader(f)
            for row in c:
                c_id = row["ID"]
                u_first = row["First"]
                u_last = row["Last"]
                u_email = row["Email"]
                if c_id not in m:
                    m[c_id] = []
                if len([ x for x in m[c_id] if x["Email"] == u_email]) == 0:
                    m[c_id].append({
                        "First": u_first,
                        "Last": u_last,
                        "Email": u_email,
                        })
    return m

if __name__ == "__main__":
    # TODO: push request context to get DB thread pooling right?
    license_data_dir = sys.argv[1]
    license_requests_dir = sys.argv[2]
    id_user_map = load_id_user_maps(license_requests_dir)

    # Make a fake request context under which to process this request
    ctx = app.test_request_context('/?next=http://example.com/')
    ctx.push()
    import_licenses_from(license_data_dir, id_user_map)
    ctx.pop()
