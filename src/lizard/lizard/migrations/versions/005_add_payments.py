from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
customer = Table('customer', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('name', String(length=256), nullable=False),
    Column('active', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('accepted_license', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('stripe_customer_id', String(length=256)),
    Column('renewal_seats', Integer),
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
    Column('stripe_charge_id', String(length=256)),
    Column('invoice_id', String(length=256)),
    Column('blob', LargeBinary),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['customer'].columns['renewal_seats'].create()
    post_meta.tables['customer'].columns['stripe_customer_id'].create()
    post_meta.tables['license'].columns['invoice_id'].create()
    post_meta.tables['license'].columns['stripe_charge_id'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['customer'].columns['renewal_seats'].drop()
    post_meta.tables['customer'].columns['stripe_customer_id'].drop()
    post_meta.tables['license'].columns['invoice_id'].drop()
    post_meta.tables['license'].columns['stripe_charge_id'].drop()
