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
              swift_url: http://192.168.8.80:8080/auth/v1.0
              swift_container: container_aerofs
              remote_storage_encryption_password: somethingunique
        - address: 192.168.50.11

If you have no Swift container available, configure a new one:

### Setting up a Swift node

You have two ways for setting up a new Swift endpoint:

* using https://github.com/aerofs/vagrant-swift-all-in-one swift-vm which force you to destroy and provision
    your VM each time you halt it.

        git clone https://github.com/aerofs/vagrant-swift-all-in-one swift-vm && cd swift-vm
        vagrant up

* using a box image shared on an AeroFS folder. Ask Mickaël, Alex, Hugues, or Matt.

        # Accept the invitation, then go to the directory
        vagrant up
        # Currently, the daemon is not launched on startup so you will have to:
        vagrant ssh
        startmain

After that, install the client to access/test/manage the node.

    pip install python-swiftclient
    # You may have to restart your shell because the newly installed `swift` executable will
    # replace the swift compiler.
    # List the containers on the node (should be empty)
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list
    # Add a container
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K post container_aerofs
    # Check
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list

Check the new VM's IP (check `/etc/syncdet/syncdet.yaml`) and remember to launch it *after* the syncdet actors.

#### Accessing it

You can access the node with these default credentials:

Username: `test:tester`

Password: `testing`

Endpoint URL: `http://192.168.8.80:8080/auth/v1.0`

If you have installed the `python-swiftclient`* package (`pip install python-swiftclient`), you can do the following to manage the node:

    # Check the connection and get an auth token (useless for us)
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing stat -v
    # Retrieve the list of containers
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list

    # Delete a container
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing delete images2
    # Create a container
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing post kikoo

    # Retrieve a file
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing list images
    swift -A http://192.168.8.80:8080/auth/v1.0 -U test:tester -K testing download images test.jpg

\*beware of the conflict with the existing `swift` compiler, you will need to reload your terminal.

### Building / Setup

See [get-started.md](get-started.html) section `Set up and run SyncDET tests`.

### Running the tests

The `teamserver` suite will cover the remote storage backend testing:

    invoke syncdet --syncdet-scenario=./system-tests/syncdet/suites/teamserver.scn
