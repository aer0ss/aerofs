# Web

## Development

There are several development workflow options. They are listed below in order
of decreasing speed.

Note: if you are editing static assets, you must do a `make` in `./web`, and
can optionally do a `make watch`, which plays nicely with Workflow 1, below.

### Workflow 1: Local Prod Soft Links

```
sudo rm -rf /opt/web/web && sudo ln -s /mnt/aerofs/src/web/web/ /opt/web/web && sudo ln -s /opt/repackaging/installers/modified /opt/web/web/static/installers
```

And to apply changes,

```
lp-ssh -c "sudo service web restart"
```

### Workflow 2: Local Prod Deploy

Build and deploy a web deb to your local prod instance.

```
lp-deploy web && lp-ssh -c "sudo service web restart"
```

### Workflow 3: Appliance Deploy

Build an appliance and test your web changes directly there. (Some additional
params might be required below).

```
invoke bake
```
