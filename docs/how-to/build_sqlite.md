sqlite-jdbc is built from source

We use a custom version of the code that disable some optional
features that are of no use to us (full text search, extensions,
thread-safety, ...), allows us to customize the loading of native
libraries and fix some issues (in the jdbc wrapper, not SQLite
itself). We should strive to stay as close to upstream as reasonably
possible.

The forked repo is: https://bitbucket.org/huguesb/sqlite-jdbc

At this time it is a private repo (we should make it public at
some point but we probably want to use a company account to do
so).

Building can be done with the default Makefile. The usual
procedure is to build the jar on OSX and the native libraries
on build VMs. This is simply for convenience and could
easily be changed if needed.

The plan in the medium-term is to use maven/gradle to
manage versions of projects within the company and SQLite
would be a perfect candidate for that. Unfortunately the
infrastructure is not yet ready for that.
