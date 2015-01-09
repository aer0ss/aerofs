This folder contains base images used to generate other image. All of base 
image provide the following facilities:

- Install container scripts to /container-scripts.

- Use /container-scripts/service-barrier as the entry point. Derived images 
should define CMD rather than overriding the entry point.
