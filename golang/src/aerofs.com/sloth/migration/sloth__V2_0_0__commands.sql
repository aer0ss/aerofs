/* 
 * No bot is needed since the message will be sent either directly to a person
*/
create table commands (
    command VARCHAR(32) CHARACTER SET latin1 PRIMARY KEY, /* slash command trigger */
    method VARCHAR(8) CHARACTER SET latin1 not null,      /* HTTP method (GET / POST) */
    url VARCHAR(256) CHARACTER SET latin1 not null,       /* outgoing endpoint*/
    token CHAR(32) CHARACTER SET latin1 not null,         /* hex encoded uuid for auth at outgoing endpoint */
    syntax VARCHAR(128) CHARACTER SET latin1,             /* command syntax, forautocomplete */
    description VARCHAR(128) CHARACTER SET latin1        /* command description, for autocomplete */
);
