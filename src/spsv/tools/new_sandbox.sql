--The three steps to create a sandbox mysql database on SP as root
--------------------------------------------------------------------
--Replace all instances of "foo" with the engineer's name
-- e.g. aerofs_sp_staging_greg
--Replace 'password' with a password desired by the engineer (but it will be
-- entered in plain text)

CREATE DATABASE aerofs_sp_staging_foo;
CREATE USER 'staging_foo'@'localhost' IDENTIFIED BY 'password';
GRANT ALL ON aerofs_sp_staging_foo.* TO 'staging_foo'@'localhost';
