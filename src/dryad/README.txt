=====
Dryad
=====

The Dryad Server is a simple, RESTful log collection service. This document describes the Dryad REST
API.

========
REST API
========

Upload logs

    Appliance

        Definition: POST /{org_id}/{dryad_id}/appliance/logs

            org_id: Organization ID (long).
            dryad_id: Dryad ID (UUID).

        Example:

            curl --include --request POST \
                --insecure \
                --header "Content-Type:application/octet-stream" \
                --data-binary @test.tar.gz \
                https://dryad.aerofs.com/v1.0/0/00000000000000000000000000000000/appliance/logs

    Client

        Definition: POST /{org_id}/{dryad_id}/client/{user_id}/{device_id}/logs

            org_id: Organization ID (long).
            dryad_id: Dryad ID (UUID).
            user_id: User email address (string).
            device_id: Unique device ID (UUID).

        Example:

            curl --include -X POST \
                --insecure \
                --header "Content-Type:application/octet-stream" \
                --data-binary @test.tar.gz \
                https://dryad.aerofs.com/v1.0/0/00000000000000000000000000000000/client/matt@aerofs.com/00000000000000000000000000000000/logs

