-- Create databases.
CREATE DATABASE IF NOT EXISTS ___sp_schema___;
-- Create user.
GRANT ALL ON ___sp_schema___.* to ___sp_username___ IDENTIFIED BY '___sp_password___';
