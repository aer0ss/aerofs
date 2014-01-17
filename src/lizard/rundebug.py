#!env/bin/python
from lizard import app, migrate_database

migrate_database()
app.run(debug=True)
