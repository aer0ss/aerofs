from lib.app import aerofs_proc


def main():
    aerofs_proc.stop_all()
    aerofs_proc.run_ui()

spec = {'default': main}
