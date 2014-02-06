Running the Web site locally

## Public Mode

Simply set up your system as per the developer setup doc and run the following:

    cd ~/repos/aerofs/src/web
    ./development/setup.sh
    ./development/run.sh public

## Private Mode

TODO (MP) make this easier!

On your local system you will have to point "localhost" to your local prod system. This is required because that is the place the web code looks for configuration. Add this to /etc/hosts:

    192.168.51.150 localhost

Then on your local system run the following:

    sudo touch /var/aerofs/configuration-initialized-flag
    cd ~/repos/aerofs/src/web
    ./development/setup.sh
    ./development/run.sh private

## Viewing the website

The website will be available at http://0.0.0.0:6543/
