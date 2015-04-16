# Web

## Development

There are several development workflow options. They are listed below in order
of decreasing speed.

Note: if you are editing static assets, you must do a `make` in `./web`, and
can optionally do a `make watch`, which plays nicely with Workflow 1, below.

### Workflow 1: Docker Mounts

Set up your docker mounts using the following script.

```
./development/setup-docker.sh
```

After the above has been applied, pserve will automatically reload your
changes. Don't forget to checkout the patched build files.

### Workflow 2: Docker Deploy

Build and deploy a web package to your local docker cluster.

```
cd ~/Repos/aerofs
make -C src/web && dk-reload web
```

### Workflow 3: Appliance Deploy

Build an appliance and test your web changes directly there. (Some additional
params might be required below).

```
invoke bake
```
