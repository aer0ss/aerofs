from sqlalchemy import *
from migrate import *

from migrate.changeset import schema
pre_meta = MetaData()
post_meta = MetaData()

# This migration file was semi-manually generated, because SQLAlchemy doesn't support Full Text Indexes
# We have to call the CREATE FULLTEXT INDEX command manually, and the corresponding DROP index

def upgrade(migrate_engine):
    # Upgrade operations go here. Don't create your own engine; bind
    # migrate_engine to your metadata
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    conn = post_meta.bind.connect()
    conn.execute("CREATE FULLTEXT INDEX idx_customers_ftxt on customer (name)")
    conn.execute("CREATE FULLTEXT INDEX idx_admin_ftxt on admin (email)")

def downgrade(migrate_engine):
    # Operations to reverse the above upgrade go here.
    pre_meta.bind = migrate_engine
    post_meta.bind = migrate_engine
    conn = post_meta.bind.connect()
    conn.execute("DROP INDEX idx_customers_ftxt on customer")
    conn.execute("DROP INDEX idx_admin_ftxt on admin")
