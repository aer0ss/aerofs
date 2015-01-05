Setup Maven
===
Setup-maven is a dummy project which is used to download various dependencies
from the Nexus repos to the local Maven repository.

The project lists all dependencies used by various parts in the AeroFS
codebase. When the project is built, maven will download the project's
dependencies to the local Maven repository.

To build: `mvn compile`

Motivation
===
IntelliJ has a bug which prevented the IDEA from automatically download some
jars from some repos. Thus, this project was created as a workaround to
manually force Maven to download the libraries we need.
