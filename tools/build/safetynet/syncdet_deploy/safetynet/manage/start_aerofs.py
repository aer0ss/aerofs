from safetynet import client

def start_aerofs():
    client.instance().start_aerofs()

spec = { 'default': start_aerofs, 'timeout': 15 }
