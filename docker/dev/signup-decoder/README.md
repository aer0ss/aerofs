The Signup Decoder is a containerized service that is supposed to run side-by-side with other AeroFS containers.  
Its REST API takes a user id as input and outputs the user's latest sign-up code.

Some system tests use this service to create new users without the need of parsing signup emails.

Usage:

    $ curl locahost:21337/get_code?userid=email@address