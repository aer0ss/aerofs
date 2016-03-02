from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
customer = Table('customer', pre_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('name', String(length=256), nullable=False),
    Column('active', Boolean, nullable=False, default=ColumnDefault(True)),
    Column('accepted_license', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('stripe_customer_id', String(length=256)),
    Column('renewal_seats', Integer),
)

customer = Table('customer', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('name', String(length=256), nullable=False),
    Column('accepted_license', Boolean, nullable=False, default=ColumnDefault(False)),
    Column('stripe_customer_id', String(length=256)),
    Column('renewal_seats', Integer),
)

def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    pre_meta.tables['customer'].columns['active'].drop()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    pre_meta.tables['customer'].columns['active'].create()
