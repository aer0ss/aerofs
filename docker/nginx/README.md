The build process of this image is, peculiar, to say the least.

For some reason, Alpine maintainers are sticking to nginx 1.8.x
at this time and we need 1.9.x so we have to build a package
from source.

Building from source is (reasonably) easy but it takes a while
and we're not going to update nginx very often so this is the
first image to use the "side-build" or "two-step-build" approach,
wherein a Docker-based recipe is used to build a binary package
which is stored in the tree (ideally we'd have an out-of-tree
repository of binary artifacts, like git-lfs...) and the regular
build process uses this package directly.

This approach is not well-supported by docker so the build script
goes through some creative gymnastics to produce the base nginx
image on top of which AeroFS-specific content is layered.
