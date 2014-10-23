# Baseline Sample Project

## Features

* Flyway to demonstrate how to setup a database schema
* JDBI for database access
* Input validation
* Use of JAX-RS root resources and injected sub resources

## Setup

Only two things have to be setup:

# Log directory
# Database

### Log Directory

Create the log directory using the following command on *nix.

```

mkdir -p /var/log/simple

```
Ensure that the server process has permissions to write to that directory.


### Database

The database url that `Simple` connects to is specified in `simple.yml`.
To setup that database with the appropriate user and password, run the following commands:
```
# in shell

mysql -u root

# in the mysql root shell...

create user 'simple'@'localhost' identified by 'password';

grant all privileges on simple.* to 'simple'@'localhost';

```

## Building

Simply run `gradle build`, `gradle test` or `gradle dist` to build, run tests, or build the distribution zips.