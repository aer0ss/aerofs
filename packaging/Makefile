
.PHONY: all debs upload clean test packagelist

# Default the bin to staging unless the user specifies otherwise. Set this in the environment.
BIN?=$(shell whoami | tr [a-z] [A-Z])
TARGET_REPOSITORY=$(BIN) # The target repository is set to the bin.

# List of known packages that can be built in alphabetical order.
PACKAGES= \
	auditor \
	bifrost \
	bootstrap \
	bunker \
	ca-server \
	ca-tools \
	charlie \
	cmd-tools \
	command \
	config \
	db-backup \
	dryad \
	havre \
	identity \
	jeq-tools \
	lizard \
	log-collection \
	middleman \
	pagerduty \
	repackaging \
	rocklog \
	sanity \
	servlet-tools \
	sp \
	sparta \
	stun \
	verification \
	verkehr \
	web \
	zephyr \

DEBS=$(addprefix debs/, ${PACKAGES:=.deb})

all: ${PACKAGES}

${PACKAGES}: %: %.deb
	@for file in build/*.deb build/*.ver; do mv $$file debs; done

debs:
	@mkdir -p debs

packagelist: debs
	@echo "updating package list debs/Packages ..."
	@cd debs && dpkg-scanpackages . > Packages && cd ../

%.deb:
	@# Ensure build folder exists
	@mkdir -p build
	@# Wipe out the work folder in the build dir.
	@rm -rf build/$(shell basename ${@:.deb=})
	@# If one exists, copy all the resources from packaging/<packagename> to packaging/build/<packagename>
	@if [ -d $(shell basename ${@:.deb=}) ] ; then cp -a $(shell basename ${@:.deb=}) build/ ; fi
	@# If there's an additional builder script for this package, run it.
	@if [ -x ./builders/$(shell basename ${@:.deb=.sh}) ] ; then ./builders/$(shell basename ${@:.deb=.sh}) ; fi
	@# build/<packagename> should now look like a raw-extracted .deb package,
	@# with a DEBIAN folder and all the other files to be packaged.
	@# Fetch the .ver, patch the DEBIAN/control, and build the .deb
	@./tools/deb-build.sh ${@:.deb=} $(TARGET_REPOSITORY)
	@mkdir -p debs

upload:
	@./tools/deb-upload.sh $(TARGET_REPOSITORY)

upload-versions:
	@./tools/deb-upload.sh $(TARGET_REPOSITORY) true

clean:
	@rm -rf build debs versions *.deb *.ver *.txt

