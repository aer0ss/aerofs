========================
Command Server Interface
========================

=======
Enqueue
=======

    POST http://<host>:9020/devices/<did>/enqueue/<command_type>
        On success:
            200: successfully enqueued.
        On failure:
            500: if the DID is malformed.
            404: if the device has never been online.

    GET http://<host>:9020/command_types
        Returns a list of valid command types.

N.B. this should be wirewalled off and only accessible on the VPC.

===
GET
===

TODO

N.B. this interface can be public.

===
ACK
===

TODO

N.B. user auth is required here.
