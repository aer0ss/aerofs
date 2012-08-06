
.PHONY: all debs upload clean test

# default the mode to staging unless the user specifies otherwise
MODE?=STAGING

PACKAGES= \
	common \
	kvm \
	vmhost \
	admin \
	ca-server \
	ca-tools \
	zephyr \
	verkehr \
	pagerduty \
	web \
	cmd-server \
	cmd-tools \
	sp \
	sv \
	syncstat \

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
	@./tools/deb-build.sh ${@:.deb=} $(MODE)
	@mkdir -p debs

upload:
	@./tools/deb-deploy.sh $(MODE)

clean:
	@rm -rf debs versions *.deb *.ver *.txt

test:
	@./tools/run-packaging-tests.sh
