# Ship Enterprise

_It's a ship that carries enterprise-grade containers_<br />
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
    - `cloudinit` delivers a cloud-config file only for any clouds that support
    CoreOS base images.
- `vm-cluster`: Configuration data to run the app on a cluster of self-hosted VMs *
- `gke`: Configuration data for Google Container Engine *
- `ecs`: Configuration data for Amazon EC2 Container Service *

\*: Future implementations

## Developer manual

Building deliverables for your customers is a 3-step process: 0: create crane.yml.
1: build the Loader. 2: generate one or more appropriate output formats (3. push through your CI).

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
    $ docker run --rm coolapp/loader verify coolapp/loader
    
The Loader image's name is specified as the only command argument.

### Step 2. Build the system

First, make sure that all the Docker images required by your app are locally accessible,
i.e. `docker run <image>:latest` should work for all the images.

Then, define a "ship.yml" file with the following content:

    loader: coolapp/loader
    repo: registry.hub.docker.com
    target: default
    hostname: coolapp
    swap-size: 4096
    
    # Optional
    push-repo: internal.coolapp.com
    
    # The following keys are mandatory only if the output includes one of "preloaded" and "bare"
    vm-image-name: coolapp-appliance
    vm-disk-size: 51200
    vm-ram-size: 3072
    vm-cpus: 2

- `loader`: your Loader's image name. It should contain no tags or repo names.
- `repo`: URL to your app repository.  After the initial launch, the app can change its value by calling Loader API.
- `target`: the target container or group to be launched by the Loader. The target should exist in crane.yml.
"default" loads the default group or all the containers if the default group is not defined. See crane's doc for
detail on targets. After the initial launch, the app can change its value by calling Loader API.
- `hostname`: the hostname of the appliance. It will be visible to the end user as part of
the bash command-line prompt.
- `swap-size`: The swap size of the appliance in MB. 0 to disable swap.
- `push-repo`: (optional) the target registry `push-images.sh` pushes the container images to, if it's different from `repo`.
- `vm-image-name`: the file name prefix of VM images. If it's "foo", the images
will be named "foo.ova", "foo.qcow2", and so on.
- `vm-disk-size`, `vm-ram-size`: sizes are in MB. 

Lastly, call "vm/build.sh" to generate outputs to folder "out":

    $ <path_to_ship>/vm/build.sh cloudinit,preloaded ship.yml out
    
At the time being only `cloudinit` and `preloaded` are supported as output formats.

### Step 3. Deliver the system

The build script prints the paths to build artifacts including cloud-config file and
VM images at the end of the process. Simply deliver these artifacts to your customers
or upload them to your CDN.

When the system launches, all output formats except `vm/preloaded` pull container images
from the registry defined in the ship.yml file. Therefore, in addition to the build
artifacts, you also need to push container images to the registry.

The script `push-images.sh` pushes all the application images in localhost to the 
registry specified by ship.yml's `repo` field. If `push-repo` field is present, it
will be used instead. Once all the images are pushed, the script updates the Loader's
`latest` tag in the registry to point to the newly pushed version. This last step
makes the update available to the public.


### Loader API

Your app's containers may optionally use the Loader's REST API to control Loader and
monitor its state. All API calls should prefix the URL with a version string, e.g.
`GET /v1/boot`. The latest version is "v1".
  
- `POST /boot`
- `POST /boot/{target}` reboots to a specific target. The Loader restarts, too,
after returning the response to the client.
Use "current" to refer to the current target. It is also the default value if the
target field is absent.
The "default" target refers to the default group defined in crane.yml or all the 
containers if no default group is defined.
The command persists across host reboots. That is, When the host computer restarts,
the Loader loads the app using the target specified in the last `POST /boot`.

- `GET /boot` gets boot information. It returns a JSON body as follows:

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

- `GET /containers` returns a map of container names and image names as defined in the "containers"
section of crane.yml. The names may be altered to reflect actual container names.
For example, registry and tag strings may be prefixed and suffixed to the names. The client may
used the returned information to query the host about the contianers. The map includes
all the containers defined in crane.yml rather than only those launched by the current boot target.

      {
        "foo-1.2.3": "registry.coolapp.com/foo:1.2.3",
        "bar-1.2.3": "registry.coolapp.com/bar:1.2.3",
        ...
      }

- `GET /tags/latest/{registry}` returns the latest tag available in the given registry.

- `POST /images/pull/{registry}/{tag}` pulls all the application images of the given tag from
the given registry. The list of the images is retrieved from the Loader image of the tag.
Status code `202 Accepted` is returned on success, and `409 Conflict` if the last pulling is
ongoing.

- `GET /images/pull` returns the status of the last `POST /images/pull`.
It replies with one of the following responses:

      202: { "status": "pulling", "pulled": 8, "total": 20 }
  
      200: { "status": "done" }

      500: { 
        "status": "error",
        "message": "unable to reach host"
      }

  When pulling, the value of `total` is zero if and only if the Loader container is being pulled.
  
  The returned status is "done" if no `POST /images/pull` has been invoked.
    
- `POST /switch/{registry}/{tag}/{target}` kills all the currently running containers,
creates new containers using the images at the specified registry and tag, and finally starts
the new containers defined in the target group and their dependencies.

  When creating new containers, its volume data will be copied from its counterpart container.
  The counterpart is identified by the same name in crane.yml as the new container, and runs
  in the previous system before the switch. Only volumes that exist in both containers are copied.

  A previous call to `switch` might have created the containers already. In this case,
  the containers are started as is. Note that it may be dangerous to start containers as is without
  data copying, as its data may have become out of sync with its counterpart. If unsure, always
  `POST /gc` before switching.

- `POST /gc` removes old containers, their volume data, and container images, that are
left behind from `POST /switch`.

### Web access

The system implements a Web server that shows Docker image pulling progress as soon as the VM
starts. It runs on port 80 of the VM and stops right before launching application contianers.
After the Web server stops, the front-end code continuously polls the URL
`http://<ip_of_vm>/ship-ready`. On the first successful response of this URL,
code redirects the browser to `http://<ip_of_vm>`, which is expected to be the application's
front page.

To integrate with this Web access, the application shall:

 1. serve port 80 in plain HTTP as the application's front page. Redirects are allowed;
 2. return status code 2xx on the route `/ship-ready` if and only if the application is
    ready to serve requests.

### Testing and CI

You may test the system in your development environment:

    $ <path_to_ship>/emulate.sh ship.yml default

It launches all the containers in the default container group using the local docker
command. This method always uses the default registry and the `latest` tag, and ignores
all the `repo` and `tag` parameters passed into the Loader API.

You may also test the console service and appliance banner in development environment:

    $ docker run -it coolapp/loader simulate-getty

It simulates the console screen on your terminal. Because the simulation has no
privileges to modify the host OS, menu options that require root
privilege will not succeed. Press [^C] and enter "Y" to exit simulation.

To test the generated VM image in Continuous Integration systems, you may follow
[these instructions](https://github.com/coreos/coreos-cloudinit/blob/master/Documentation/config-drive.md)
to inject commands into the image. It is useful, say, if you want to set up a
[static IP](https://coreos.com/docs/cluster-management/setup/network-config-with-networkd/)
so test suites can easily find the VM.


## IT administrator manual

### Console access

The VM's virtual terminal allows admins to view and modify the VM's networking settings.
Additionally, the console has two commands that are hidden from the menu:

Type `logs` to show Docker logs of a given container running at the current tag (i.e. version).

Type `root-shell` to launch an interactive shell with root privileges. An environmental variable
`TAG` will be set to be the current tag. You may use it to access the containers conveniently:

    $ docker logs loader-$TAG
    
### Web access

As soon as the VM starts the admin can visit the VM's IP on a browser to monitor
appliance loading progress. It is particularly useful for the `cloudinit` output format
as it may take time to pull application images from the Internet. After the application is
successfully launched, the Web UI automatically redirects the browser to the application's
front page.

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
