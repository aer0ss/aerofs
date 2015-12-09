from sqlalchemy import *
from migrate import *


from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()
hpc_deployment = Table('hpc_deployment', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('subdomain', String(length=32), primary_key=True, nullable=False),
    Column('customer_id', Integer, nullable=False),
    Column('server_id', Integer, nullable=False),
)

hpc_server = Table('hpc_server', post_meta,
    Column('create_date', DateTime, nullable=False),
    Column('modify_date', DateTime, nullable=False),
    Column('id', Integer, primary_key=True, nullable=False),
    Column('docker_url', String(length=256)),
    Column('public_ip', String(length=15)),
)


def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['hpc_deployment'].create()
    post_meta.tables['hpc_server'].create()


def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    post_meta.tables['hpc_deployment'].drop()
    post_meta.tables['hpc_server'].drop()
