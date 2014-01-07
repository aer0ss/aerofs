from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
admin = Table('admin', pre_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('email', String, nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('name', String, nullable=False),
    Column('salt', String, nullable=False),
    Column('pw_hash', String, nullable=False),
    Column('active', Boolean, nullable=False),
    Column('notify_security', Boolean, nullable=False),
    Column('notify_release', Boolean, nullable=False),
    Column('notify_maintenance', Boolean, nullable=False),
)

admin = Table('admin', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('email', Unicode(length=256), nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('first_name', String(length=256), nullable=False),
    Column('last_name', String(length=256), nullable=False),
    Column('phone_number', String(length=256)),
    Column('job_title', String(length=256)),
    Column('salt', String(length=89), nullable=False),
    Column('pw_hash', String(length=89), nullable=False),
    Column('active', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_security', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_release', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_maintenance', Boolean, nullable=False, default=ColumnDefault(True)),
)

unbound_signup = Table('unbound_signup', pre_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('signup_code', String(length=45), nullable=False),
    Column('email', Unicode(length=256), nullable=False),
)

unbound_signup = Table('unbound_signup', post_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('signup_code', String(length=45), nullable=False),
    Column('email', Unicode(length=256), nullable=False),
    Column('first_name', String(length=256), nullable=False),
    Column('last_name', String(length=256), nullable=False),
    Column('company_name', String(length=256), nullable=False),
    Column('phone_number', String(length=256)),
    Column('job_title', String(length=256)),
)

# This migration is a little hairier since you can't add nonnull columns to a
# table through an ALTER TABLE.  Fortunately, we have no data, so we can just
# drop the whole table and create a new one.
def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine

    pre_meta.tables['admin'].drop()
    post_meta.tables['admin'].create()

    pre_meta.tables['unbound_signup'].drop()
    post_meta.tables['unbound_signup'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine

    post_meta.tables['unbound_signup'].drop()
    pre_meta.tables['unbound_signup'].create()

    post_meta.tables['admin'].drop()
    pre_meta.tables['admin'].create()
