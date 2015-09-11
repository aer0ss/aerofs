-- Migration to add column for timestamp for user password creations

alter table sp_user add column u_passwd_created_ts timestamp not null default current_timestamp;
