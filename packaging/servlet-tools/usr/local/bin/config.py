def get_config(filename):
    with open(filename) as f:
        return dict((line.split("=", 1) for line in f.read().splitlines()))

config = get_config("/etc/aerofsdb.conf")
