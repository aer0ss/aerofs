from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
bound_invite = Table('bound_invite', post_meta,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('invite_code', String(length=45), nullable=False),
    Column('email', Unicode(length=256), nullable=False),
    Column('customer_id', Integer, nullable=False),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['bound_invite'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['bound_invite'].drop()
