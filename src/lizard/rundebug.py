#!env/bin/python
import sys
from lizard import create_app, migrate_database

internal = len(sys.argv) > 1 and sys.argv[1] == "internal"

app = create_app(internal)
migrate_database(app)
app.run(debug=True)
