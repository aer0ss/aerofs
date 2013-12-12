from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
admin = Table('admin', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('email', Unicode(length=256), nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('name', Unicode(length=256), nullable=False),
    Column('salt', String(length=89), nullable=False),
    Column('pw_hash', String(length=89), nullable=False),
    Column('active', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_security', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_release', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('notify_maintenance', Boolean, nullable=False, default=ColumnDefault(True)),
)

customer = Table('customer', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('name', String(length=256), nullable=False),
    Column('active', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('accepted_license', Boolean, nullable=False, default=ColumnDefault(False)),
)

unbound_signup = Table('unbound_signup', post_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('signup_code', String(length=45), nullable=False),
    Column('email', Unicode(length=256), nullable=False),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['admin'].create()
    post_meta.tables['customer'].create()
    post_meta.tables['unbound_signup'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['admin'].drop()
    post_meta.tables['customer'].drop()
    post_meta.tables['unbound_signup'].drop()
