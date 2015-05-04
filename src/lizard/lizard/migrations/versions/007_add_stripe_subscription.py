from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
license = Table('license', pre_meta,
    Column('create_date', DATETIME, nullable=False),
    Column('modify_date', DATETIME, nullable=False),
    Column('id', INTEGER, primary_key=True, nullable=False),
    Column('customer_id', INTEGER, nullable=False),
    Column('state', INTEGER, nullable=False),
    Column('seats', INTEGER, nullable=False),
    Column('expiry_date', DATETIME, nullable=False),
    Column('is_trial', BOOLEAN, nullable=False),
    Column('allow_audit', BOOLEAN, nullable=False),
    Column('blob', BLOB),
    Column('invoice_id', VARCHAR(length=256)),
    Column('stripe_charge_id', VARCHAR(length=256)),
    Column('allow_device_restriction', BOOLEAN, nullable=False),
    Column('allow_identity', BOOLEAN, nullable=False),
    Column('allow_mdm', BOOLEAN, nullable=False),
)

license = Table('license', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('state', Integer, nullable=False, default=ColumnDefault(0)),
    Column('seats', Integer, nullable=False),
    Column('expiry_date', DateTime, nullable=False),
    Column('is_trial', Boolean, nullable=False),
    Column('allow_audit', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('allow_identity', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('allow_mdm', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('allow_device_restriction', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('stripe_subscription_id', String(length=256)),
    Column('invoice_id', String(length=256)),
    Column('blob', LargeBinary),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    pre_meta.tables['license'].columns['stripe_charge_id'].drop()
    post_meta.tables['license'].columns['stripe_subscription_id'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    pre_meta.tables['license'].columns['stripe_charge_id'].create()
    post_meta.tables['license'].columns['stripe_subscription_id'].drop()
