from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
domain = Table('domain', post_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('mail_domain', String(length=256), nullable=False),
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('verify_date', DateTime, nullable=True),
)

appliance = Table('appliance', post_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('customer_id', Integer, index=True, nullable=False),
    Column('hostname', String(length=256), nullable=False),
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['domain'].create()
    post_meta.tables['appliance'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['domain'].drop()
    post_meta.tables['appliance'].drop()
