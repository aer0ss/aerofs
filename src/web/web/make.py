from subprocess import call

def make():
    call(['make', '-j8'])


if __name__ == '__main__':
    make()