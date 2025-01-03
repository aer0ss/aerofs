#!env/bin/python
# You have a model (A) that represents your current DBs.
# You want to transform that into a new model (B).
#
# To create the migration script:
# 1) Have (A) on the filesystem.
# 2) Populate the database with the schema described by version (A).
#    This will be done if you simply run the app, since it auto-applies all
#    migrations.
# 3) Edit models.py (A) into (B) on the filesystem.
# 4) Run this script with a description of the changes as argv[1].

# Note that this script does not work properly - there is an issue
# with Boolean vs Tinyint schema types in sqlalchemy and mysql.
# http://stackoverflow.com/questions/20250901/python-sql-alchemy-migrate-valueerror-too-many-values-to-unpack-when-migrat

import imp
import os.path
import sys

from migrate.versioning import api

from lizard import create_app, db

app = create_app(False)

SQLALCHEMY_DATABASE_URI = app.config['SQLALCHEMY_DATABASE_URI']
SQLALCHEMY_MIGRATE_REPO = app.config['SQLALCHEMY_MIGRATE_REPO']

# come up with a name for the migration
migration_description = "migration"
if len(sys.argv) > 1:
    migration_description = sys.argv[1].replace(' ', '_')

# Pick the next filename for this script
current_db_version = api.db_version(SQLALCHEMY_DATABASE_URI, SQLALCHEMY_MIGRATE_REPO)
new_migration_script = os.path.join(SQLALCHEMY_MIGRATE_REPO, "versions",
        "{0:03d}_{1}.py".format(current_db_version + 1, migration_description))

# Get current model from DB, save into a temporary module
tmp_module = imp.new_module('old_model')
old_model = api.create_model(SQLALCHEMY_DATABASE_URI, SQLALCHEMY_MIGRATE_REPO)
exec old_model in tmp_module.__dict__

# Generate a new update script and save it to disk
script = "# this script is generated by create_db_migrate_script.py\n" + \
         api.make_update_script_for_model(SQLALCHEMY_DATABASE_URI, SQLALCHEMY_MIGRATE_REPO, tmp_module.meta, db.metadata)
with open(new_migration_script, "wt") as f:
    f.write(script)
    print script

print "New migration saved as", new_migration_script
print "Current database version:", api.db_version(SQLALCHEMY_DATABASE_URI, SQLALCHEMY_MIGRATE_REPO)
print "Restart the app to update your database to the new version"
