# Bunker

## Development

There are several development workflow options. They are listed below in order
of decreasing speed.

Note: if you are editing static assets, you must do a `make` in `./bunker`, and
can optionally do a `make watch`, which plays nicely with Workflow 1, below.

### Workflow 1: Docker Mounts

Set up your docker mounts using the following script.

```
./development/setup-docker.sh
```

After the above has been applied, CherryPy will automatically reload your
changes to make files. For python changes, run `dk-reload bunker`.

TODO: make CherryPy do automatic reloads, like we have done with Pyramid in
web.

### Workflow 2: Docker Deploy

Build and deploy a bunker package to your local docker cluster.

```
cd ~/Repos/aerofs
make -C src/bunker && dk-reload bunker
```

### Workflow 3: Appliance Deploy

Build an appliance and test your bunker changes directly there. (Some additional
params might be required below).

```
invoke bake
```
