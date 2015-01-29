Canary Build system v1 requirements, design, & implementation
===

# Requirements


- Any HC users can choose to run Canary if they want. Canary doesn't
need to be available for PC users at this point.

- For v1 we do NOT require that Canary and Stable builds to run
side-by-side on one computer.

- However, users should be able to switch between Canary and Stable
relatively easily.

- Canary and Stable should be able to communicate and sync files
amongst each other.

- The new build system should minimize the conditional statements used
to build Canary, to reduce complexity in build.

- Any conditional statements should be inserted as late as possible in
the build and deploy process, to minimize dependency to the condition.

- Protocol compatible changes between Canary and Stable will be
treated manually as special cases and don't need to be considered in
v1.


# Design

- Canary and Stable versions will be simply two pointers into the same
release train with a certain distance. For example, If Stable version
is 0.4.123, then Canary can be 0.4.128.

- Whenever a Canary is ready to become Stable, we simply update the
Stable version to point to that Canary build. The Canary build
artifacts with be used as Stable without additional build or
deployment.

- We will set up a JIRA project to track bug reports for Canary. It
will also be used to determine whether a Canary is Stable ready.


# Implementation

- We will add a new version file, named "canary.ver", in the
same location where the existing "current.ver" is hosted on S3.

- All HC build and deploy scripts will read and write
"canary.ver" instead of "current.ver" to determine the next
version to be built and deployed.

- On the client side, if there is a file named "canary" under RTROOT,
the client will use "canary.ver" instead of "current.ver" to
check for updates.

- On the client side, the About dialog shows a "Canary" label if the canary file exists.

- We will no longer generate download URLs with no vesion numbers (e.g. AeroFSInstaller.dmg). Instead, aerofs.com/download will rely on current.ver to generate versioned download URLs.

- When a Canary is ready for Stable, we manually update "current.ver."

- When a user wants to switch from Stable to Canary, touch "canary" in
RTROOT. The app will become Canary in an hour (or force a manual update).

- When a user wants to switch back, remove "canary" and the app will become Stable when the Stable version catches up Canary. Note that the user should never roll back by replacing Canary with an earlier Stable version; otherwise it might cause database schema and protocol incompatibilities and in turn unpredicted behaviors.
