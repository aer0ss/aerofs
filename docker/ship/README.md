# Ship Enterprise

_It's a ship to carry enterprise-grade containers_<br />
_It's to ship apps to enterprises_<br />
_It's the starship Enterprise for fearless engineers_

Ship is a tool that automates delivery and self-managing of a multi-container
app on various cloud platforms. It takes the following as input:

1. The app's Docker images.
2. A crane.yml file that describes the structure of the app using the
[crane](https://github.com/michaelsauter/crane) format.
3. A yaml file that defines a handful of paramters for Ship.

It generates one or more output formats as follows:

## Output format

- `vm`: A single appliance VM that contains all the app containers. It provides various
deployment options:
    - `preloaded` delivers a standalone VM image with all the containers preloaded.
    - `bare` delivers a minimum image to launch the VM. Containers will be pulled at
    first run from the registry defined at build time.
    - `cloud-init` delivers a cloud-config file only for any clouds that support
    CoreOS base images.
- `vm-cluster`: Configuration data to run the app on a cluster of self-hosted VMs *
- `gke`: Configuration data for Google Container Engine *
- `ecs`: Configuration data for Amazon EC2 Container Service *

\*: Future implementations

## Developer manual

Building deliverables for your customers is a 3-step process: 0: create crane.yml.
1: build the Loader. 2: generate one or more appropriate output formats (3. push through your CI).

Note: currently Ship only supports OSX with boot2docker. See
build.sh:setup_preload_registry().

### Step 0. Create crane.yml

Create a standard [crane](https://github.com/michaelsauter/crane) yaml file to describe your
app. The same file can and should be used for development and testing of your apps as well.

The file must include one and only one container with the image name identical to the Loader image
defined in ship.yml (see Step 2). Otherwise the system will fail when building the preloaded VM image or
on the first run. Using the Loader's API from other containers is optional.

### Step 1. Build the Loader

Loader is a meta-container that loads and upgrades your app's other containers.
Optionally, the other containers may use an [API](#Loader API) to control and monitor Loader.
You need to build a Loader specifically for your app.

To build the Loader image, simply inherit it from "shipenterprise/loader", add the following files:

- /crane.yml: A [crane-format](https://github.com/michaelsauter/crane) file to define your app's structure. 
It should define at least one container that uses the Loader image.
- /tag: only contains your app's version string such as "v1.2.3nightly".
- /banner: Optional. An ASCII art file shown on the VM virtual console's welcome screen.

Here is an example Dockerfile:

    FROM shipenterprise/vm-loader
    COPY crane.yml /crane.yml
    COPY version /tag

You may run the `verify` command to verify the two files are correctly installed:

    $ docker build -t coolapp/loader .
    $ docker run --rm coolapp/loader verify

### Step 2. Generate outputs

First, make sure that all the Docker images required by your app are locally accessible,
i.e. `docker run <image>:latest` should work for all the images.

Then, define a "ship.yml" file with the following content:

    loader-image: coolapp/loader
    repo: registry.coolapp.com
    target: default
    
    vm-image-name: coolapp-appliance
    vm-host-name: coolapp
    vm-disk-size: 102400
    vm-ram-size: 3072
    vm-cpus: 2

- `loader-image`: your Loader's image name. It should contain no tags or repo names.
- `repo`: URL to your app repository.  After the initial launch, the app can change its value by calling Loader API.
- `target`: the target container or group to be launched by the Loader. The target should exist in crane.yml.
"default" loads the default group or all the containers if the default group is not defined. See crane's doc for
detail on targets. After the initial launch, the app can change its value by calling Loader API.
- `vm-image-name`: the file name prefix of VM images. If it's "foo", the images
will be named "foo.ova", "foo.qcow2", and so on.
- `vm-host-name`: the hostname of the VM. It will be visible to the end user as part of
the bash command-line prompt.
- `vm-disk-size`, `vm-ram-size`: sizes are in MB.

Lastly, call "vm/builder/build.sh" to generate VM images to folder "out":

    $ <path_to_ship>/vm/builder/build.sh ship.yml out


### Loader API

Your app's containers may optionally use the Loader's REST API to control Loader and
monitor its state. All API calls should prefix the URL with a version string, e.g.
`GET /v1/boot`. The latest version is "v1".
  
- `POST /boot`
- `POST /boot/{registry}`
- `POST /boot/{registry}/{tag}`
- `POST /boot/{registry}/{tag}/{target}`: reboot to a specific registry, tag, and target.
Use "current" to refer to the registry, tag, or target currently used by Loader. When a parameter
is absent from the URL, "current" is used.
The "default" target refers to the default group defined in crane.yml or all the containers if no default group is defined.
That the command persists across host reboots. That is, on a host reboot, the Loader loads the app using the repo, tag, and target
specified in the last `POST /boot`.

- `GET /boot`: get boot information. It returns a JSON body as follows:

      {
        "id": "abcdef...",
        "registry": "registry.coolapp.com",
        "tag": "v1.2.3rc",
        "target": "default"
      }
        
    "id" is a string uniquely identifies each Loader launch. It changes every time Loader
    restarts. "registry", "tag", and "target" are the Docker image registry, Docker image tag,
    and crane target used to launch the app's containers including Loader. These three fields
    can be updated by the `POST /boot` call, and the change will be reflected after the Loader
    finishes rebooting.

- `GET /containers`: return a map of container names and image names as defined in the "containers"
section of crane.yml. The names may be altered to reflect the actual names used by the host.
For example, registry and tag strings may be prefixed and suffixed to the names. The client may
used the returned information to query the host about the contianers and images. The map includes
all the containers rather than only the containers launched by the current boot target.

      {
        "foo-1.2.3": "registry.coolapp.com/foo:1.2.3",
        "bar-1.2.3": "registry.coolapp.com/bar:1.2.3",
        ...
      }



### Testing and CI

You may test the Loader's API in your development environment:

    $ docker run coolapp/loader simulate-api registry.coolapp.com default

This allows other containers to call the Loader container's API. The API acts as if it runs with the given registry and target name. `POST /boot` in simulation mode does not restart application containers or itself.

You may also test the console service and your banner in development environment:

    $ docker run -it coolapp/loader simulate-getty

This will simulate the console screen on your terminal. Because the simulation has no
privileges to modify the host OS, menu options that require root
privilege will not succeed. Press [^C] and enter "Y" to exit simulation.

To test the generated VM image in Continuous Integration systems, you may follow
[these instructions](https://github.com/coreos/coreos-cloudinit/blob/master/Documentation/config-drive.md)
to inject commands into the image. It is useful, say, if you want to set up a
[static IP](https://coreos.com/docs/cluster-management/setup/network-config-with-networkd/)
so test suites can easily find the VM.

## IT admin & site engineer manual

The VM's console service has two hidden commands:

**logs** shows logs of a given Docker container running at the current tag (i.e. version).

**root-shell** launches an interactive shell with root privileges. An environmental variable
`TAG` will be set to be the current tag. You may use it to access the containers conveniently:

    $ docker logs loader-$TAG

## Internal design notes

### Assumptions

To enable live upgraeds, we use the tag as part of container names. Hence, we assume legal tag names are also legal
container names.

### Persistence

The repo, tag, and target are persisted across host reboot. We achieve this by writing the repo and target to the host filesystem,
and by reading the tag from <repo>/<loader_image>:latest. It is the Loader's responsibility to write repo and target files as well
as to tag appropriate Loader images as the latest.

### Q: Why separate Loader and the console service (getty) processes?

A: The getty process is launched only for VMs with console access. The Loader process doesn't depend on the
getty process. Additionally, there may be needs for multiple getty instances (multiple virtual terminals
and series console). However there should be only one Loader instance at a time.
