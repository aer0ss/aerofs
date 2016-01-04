Getting Started
===============

#### MySQL configuration

```
$ mysql -u root
(check that you're on version 5.6 *NOT* 5.7)
mysql> create database lizard;
mysql> exit
```

Either change the mysql user root@localhost to use no password or update the
connection string in additional\_config.py.

### Redis configuration

Run redis: `$ redis-server /usr/local/etc/redis.conf`

#### Python environment

```bash
cd ~/repos/aerofs/src/lizard
virtualenv env
env/bin/pip install -r requirements-exact.txt
env/bin/pip install --editable ../licensing
```

#### Web static content

```bash
ln -s ../web/web/static .
make -C ../web/web
```

#### Run lizard locally

- `./rundebug.py` to run the user front-end (ie: what you see at https://privatecloud.aerofs.com)
- `./rundebug.py internal` to run the administrative interface (http://privatecloud.aerofs.com:8000 - only accessible from the VPN)

Now you should be able to hit the instance on localhost:4444 in your browser.


#### Run Celery (for Hosted Private Cloud)

If you're working on Hosted Private Cloud, you need to run the Celery worker to process the tasks.

1. Run Celery: `$ ./env/bin/celery worker -A "lizard.celery_worker.celery" -l DEBUG`


#### Create test user with real license

This will be needed to test the HPC Deployment flow locally. When you run the
`create_test_user_and_license.py` script, a user is created with a fake license file.

To attach a real license file, open up your local mysql server as root: `mysql -uroot`

Switch into the lizard database with `use lizard;`

Then run the following:
  `UPDATE license
  SET license.blob=LOAD_FILE('~/repos/aerofs/system-tests/webdriver-lib/root/test.license') 
  WHERE license.customer_id=<test_user_id>;`

Notes
=====

On some versions of OS X, compiling the module pygpgme fails because clang
won't search the standard include paths (`/usr/local/include`).

If you're seeing error messages about include files not found while running
`env/bin/pip install -r requirements-exact.txt`, try the following:

```
export CFLAGS="${CFLAGS} -I/usr/local/include"
export LDFLAGS="${LDFLAGS} -L/usr/local/lib"
env/bin/pip install -r requirements-exact.txt
```

On el cap, openssl may also be borked, in this case add your openssl path:


```
export CFLAGS="${CFLAGS} -I/usr/local/Cellar/openssl/1.0.2d_1/include"
export LDFLAGS="${LDFLAGS} -L/usr/local/Cellar/openssl/1.0.2d_1/lib"
env/bin/pip install -r requirements-exact.txt
```
