# Bunker

## Development

There are several development workflow options. They are listed below in order
of decreasing speed.

### Workflow 1: Local Prod Soft Links

```
lp-ssh -c "sudo mv /opt/bunker/web /opt/bunker/web.bak && sudo ln -s /mnt/aerofs/src/bunker/web/ /opt/bunker/web"
```

And to apply changes,

```
lp-ssh -c "sudo service bunker restart"
```

### Workflow 2: Local Prod Deploy

Build and deploy a bunker deb to your local prod instance.

```
lp-deploy bunker && lp-ssh -c "sudo service bunker restart"
```

### Workflow 3: Appliance Deploy

Build an appliance and test your bunker changes directly there. (Some
additional params might be required below).

```
invoke bake
```
