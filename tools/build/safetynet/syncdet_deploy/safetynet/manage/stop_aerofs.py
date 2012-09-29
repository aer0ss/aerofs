from safetynet import client

def stop_aerofs():
    client.instance().stop_aerofs()

spec = { 'default': stop_aerofs, 'timeout': 15 }
