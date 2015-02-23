from lib.app import aerofs_proc


def main():
    aerofs_proc.stop_all()

spec = {'default': main}