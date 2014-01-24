#!env/bin/python
from lizard import create_app, migrate_database

app = create_app()
migrate_database(app)
app.run(debug=True)
