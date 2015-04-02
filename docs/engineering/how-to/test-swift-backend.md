# Testing Swift remote storage backend for TeamServer

## Unit testing

    gradle :src/sync:test -Dtest.single=TestSwiftBackend --info

This will use a mocked in-memory backend.

## Integration testing with syncdet

### Configuration

You will need the following syncdet *actors* configuration:

    actors:
        - address: 192.168.50.10
          details:
              team_server: true
              storage_type: SWIFT
              swift_auth_mode: basic
              swift_username: test:tester
              swift_password: testing
              swift_url: http://192.168.33.10:8080/auth/v1.0
              swift_container: container_aerofs
              remote_storage_encryption_password: somethingunique
        - address: 192.168.50.11

If you have no Swift container available, configure a new one:

### Setting up a Swift node

    git clone https://github.com/aerofs/vagrant-swift-all-in-one swift-vm && cd swift-vm
    vagrant up
    # List the containers on the node (should be empty)
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list
    # Add a containr
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K post container_aerofs
    # Check
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list

Warning: it seems the backend properly works at the first `up` of the VM. If you `halt` it, you better `destroy` it...

### Building / Setup

See [get-started.md](get-started.html) section `Set up and run SyncDET tests`.

### Running the tests

The `teamserver` suite will cover the remote storage backend testing:

    invoke syncdet --syncdet-scenario=./system-tests/syncdet/suites/teamserver.scn
