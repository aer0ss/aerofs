from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
license = Table('license', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('state', Integer, nullable=False, default=ColumnDefault(0)),
    Column('seats', Integer, nullable=False),
    Column('expiry_date', DateTime, nullable=False),
    Column('is_trial', Boolean, nullable=False),
    Column('allow_audit', Boolean, nullable=False, server_default="false", default=ColumnDefault(False)),
    Column('allow_identity', Boolean, nullable=False, server_default="false"),
    Column('allow_mdm', Boolean, nullable=False, server_default="false"),
    Column('allow_device_restriction', Boolean, nullable=False, server_default="false"),
    Column('stripe_charge_id', String(length=256)),
    Column('invoice_id', String(length=256)),
    Column('blob', LargeBinary),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['license'].columns['allow_device_restriction'].create()
    post_meta.tables['license'].columns['allow_identity'].create()
    post_meta.tables['license'].columns['allow_mdm'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['license'].columns['allow_device_restriction'].drop()
    post_meta.tables['license'].columns['allow_identity'].drop()
    post_meta.tables['license'].columns['allow_mdm'].drop()
