
.PHONY: all debs upload clean test

# default the mode to staging unless the user specifies otherwise. set this in the environment.
MODE?=STAGING
TARGET_REPOSITORY=$(MODE) # the target repository is set to the mode for now

PACKAGES= \
	admin \
	ca-server \
	ca-tools \
	cmd-server \
	cmd-tools \
	common \
	kvm \
	middleman \
	pagerduty \
	rocklog \
	servlet-tools \
	sp \
	spdb \
	sv \
	syncstat \
	synctime \
	syncstat-tools \
	jeq-tools \
	verkehr \
	bootstrap \
	devman \
	vmhost \
	web \
	zephyr \
	stun \
	config \
	updater \

DEBS=$(addprefix debs/, ${PACKAGES:=.deb})

all: ${PACKAGES}

${PACKAGES}: %: %.deb
	@for file in $$(ls *.deb *.ver); do mv $$file debs; done

# Execute package "builder" scripts. These scripts are used to "build" the
# package, that is, to move any required files over from other places in the
# repository into the required directory structure in packaging/*.
%.sh:
	@test -f builders/$(shell basename ${@:.deb=.sh}); \
		if [ $$? -eq 0 ]; then ./builders/$(shell basename ${@:.deb=.sh}); fi

%.deb: %.sh
	@./tools/deb-build.sh ${@:.deb=} $(TARGET_REPOSITORY)
	@mkdir -p debs

upload:
	@./tools/deb-upload.sh $(TARGET_REPOSITORY)

clean:
	@rm -rf debs versions *.deb *.ver *.txt

test:
	@./tools/run-packaging-tests.sh
