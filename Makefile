GIT_ROOT = $(shell git rev-parse --show-toplevel)
include $(GIT_ROOT)/Makefile.common

APPROOT = $(GIT_ROOT)/approot
# win, osx, linux/i386, linux/amd64
ifeq ($(OS),Windows_NT)
	OS = win
else
ifeq ($(shell uname -s),Darwin)
	OS = osx
else
ifeq ($(shell uname -p),x86_64)
	OS = linux/amd64
else
	OS = linux/i386
endif
endif
endif
# CLIENT, TEAM_SERVER, or STORAGE_AGENT
PRODUCT =
SIGNED =
SYNCDET_ARGS =
SYNCDET_CASES =
SYNCDET_CASE_TIMEOUT = 180
SYNCDET_CONFIG = /etc/syncdet/config.yaml
SYNCDET_EXECUTABLE = $(GIT_ROOT)/../syncdet/syncdet.py
SYNCDET_SCENARIOS =
SYNCDET_TRANSPORT = default
SYNCDET_SYNC_TIMEOUT = 180
TEAM_CITY = false
VERSION = $(shell $(GIT_ROOT)/tools/build/compute_next_version.py loader)


all:

build_client: proto
	gradle src/desktop:dist
	$(call success,"build_client")

build_cloud_config:
	$(GIT_ROOT)/docker/ship-aerofs/build-cloud-config.sh aerofs/loader
	$(call success,"build_cloud_config")

build_docker_images:
	make -C $(GIT_ROOT)/docker images
	$(call success,"build_docker_images")

build_docker_sa_images:
	make -C $(GIT_ROOT)/docker sa_images
	$(call success,"build_docker_sa_images")

build_protoc_plugins: _clean_protobuf
	make out.shell/protobuf-rpc/gen_rpc_java/protoc-gen-rpc-java
	$(call success,"build_protoc_plugins")

build_sa: proto
	gradle src/storage-agent:dist
	$(call success,"build_sa")

build_sa_vm:
	$(GIT_ROOT)/docker/ship-aerofs/build-vm.sh aerofs/sa-loader
	$(call success,"build_sa_vm")

build_vm:
	$(GIT_ROOT)/docker/ship-aerofs/build-vm.sh aerofs/loader
	$(call success,"build_vm")

clean:
	$(GIT_ROOT)/tools/build/build_protos.py clean

	rm -rf $(GIT_ROOT)/out.gradle
	rm -rf $(GIT_ROOT)/out.shell

	make -C $(GIT_ROOT)/src/web/web clean
	$(call success,"clean")

markdown:
	$(GIT_ROOT)/tools/markdown_watch.sh -r $(GIT_ROOT)/docs $(GIT_ROOT)/out.shell/docs
	$(call success,"markdown")

markdown_watch:
	$(GIT_ROOT)/tools/markdown_watch.sh -f -r $(GIT_ROOT)/docs $(GIT_ROOT)/out.shell/docs
	$(call success,"markdown_watch")

package_clients: _clean_packages
ifeq ($(PRODUCT),STORAGE_AGENT)
	$(error "Use package_sa to package the STORAGE_AGENT.")
endif
ifeq ($(PRODUCT),)
	make _package PKG_PRODUCT=CLIENT
	make _package PKG_PRODUCT=TEAM_SERVER
else
	make _package PKG_PRODUCT=$(PRODUCT)
endif
	$(call success,"package_clients")

package_sa: _clean_packages
	$(eval PKG_PRODUCT := $(shell [ -z $(PRODUCT) ] && echo "STORAGE_AGENT" || echo $(PRODUCT)))
	@if [ $(PKG_PRODUCT) != "STORAGE_AGENT" ]; then \
		echo "\033[31merror: \033[0mUse package_client to package the CLIENT or TEAM_SERVER."; \
		exit 1; \
	fi
	make _package PKG_PRODUCT=$(PKG_PRODUCT)
	$(call success,"package_sa")

prepare_syncdet:
ifeq ($(PRODUCT),STORAGE_AGENT)
	make _prepare_syncdet_storage_agent PRODUCT=STORAGE_AGENT
else
ifeq ($(PRODUCT),TEAM_SERVER)
	make _prepare_syncdet_team_server PRODUCT=TEAM_SERVER
else
ifeq ($(PRODUCT),CLIENT)
	make _prepare_syncdet_client PRODUCT=CLIENT
else
	make _prepare_syncdet_client PRODUCT=CLIENT
	make _prepare_syncdet_team_server PRODUCT=TEAM_SERVER
endif
endif
endif
	$(call success,"prepare_syncdet")

proto: out.shell/protobuf-rpc/gen_rpc_java/protoc-gen-rpc-java out.shell/protobuf-rpc/gen_rpc_objc/protoc-gen-rpc-objc out.shell/protobuf-rpc/gen_rpc_python/protoc-gen-rpc-python
	$(GIT_ROOT)/tools/build/build_protos.py build
	$(call success,"proto")

push_docker_images:
	$(GIT_ROOT)/docker/ship-aerofs/push-images.sh aerofs/loader
	$(call success,"push_docker_images")

push_docker_sa_images:
	$(GIT_ROOT)/docker/ship-aerofs/push-images.sh aerofs/sa-loader
	$(call success,"push_docker_sa_images")

push_sa_vm:
	$(GIT_ROOT)/tools/build/bootstrap push_vm aerofs/sa-loader
	$(call success,"push_sa_vm")

push_vm:
	$(GIT_ROOT)/tools/build/bootstrap push_vm aerofs/loader
	$(call success,"push_vm")

setupenv:
ifeq ($(OS),)
	$(error "OS must be defined to run setupenv.")
endif
ifeq ($(PRODUCT),)
	$(error "PRODUCT must be defined to run setupenv.")
endif
	$(GIT_ROOT)/tools/populate/populate PRIVATE $(PRODUCT) $(OS) ./resource $(APPROOT)
	$(call success,"setupenv")

syncdet:
	# TODO: ideally, these would land in the same output array, tagged with
	# type, so they could be run in order.
	# For now, all cases precede all scenarios.
	for item in $(SYNCDET_CASES); do \
		make _syncdet ARG="--case=$$item"; \
	done
	for item in $(SYNCDET_SCENARIOS); do \
		make _syncdet ARG="--scenario=$$item"; \
	done
	$(call success,"syncdet")

tag_release:
	$(GIT_ROOT)/tools/build/bootstrap tag_release aerofs/loader
	$(call success,"tag_release")

tag_sa_release:
	$(GIT_ROOT)/tools/build/bootstrap tag_release aerofs/sa-loader
	$(call success,"tag_sa_release")

test_go:
	$(GIT_ROOT)/golang/tester/run.sh aerofs.com
	$(call success,"test_go")

test_js:
	make -C $(GIT_ROOT)/src/web/web clean
	make -C $(GIT_ROOT)/src/web/web
	make -C $(GIT_ROOT)/src/web/web setup_test test
	$(call success,"test_js")

test_python: proto
ifeq ($(wildcard ./env/.*),)
	virtualenv env
endif

	make _test_python PYPROJECT=python-lib
	make _test_python PYPROJECT=licensing
	make _test_python PYPROJECT=web
	make _test_python PYPROJECT=bunker
	$(call success,"test_python")

test_system:
	make _syncdet ARG="--case=lib.cases.clean_install" SYNCDET_ARGS="--case-arg=--transport=$(SYNCDET_TRANSPORT)"
	make syncdet
	$(call success,"test_system")

test_system_archive:
	make _syncdet ARG="--case=lib.cases.move_data_to_archive_dir" SYNCDET_ARGS="--tar=archive_dir"
	$(call success,"test_system_archive")

write_version:
	mkdir -p $(GIT_ROOT)/out.gradle/packages
	echo "Version=$(VERSION)" > $(GIT_ROOT)/out.gradle/packages/current.ver
	$(call success,"write_version")


_clean_packages:
	rm -rf $(GIT_ROOT)/out.gradle/packages

_clean_protobuf:
	rm -rf $(GIT_ROOT)/out.shell/protobuf-rpc

_package:
ifeq ($(SIGNED),)
	$(error "SIGNED must be defined to run package_clients or package_sa.")
endif
	$(eval SIGNED_ARG := $(shell [ $(SIGNED) = true ] && echo "SIGNED" || echo "UNSIGNED"))
	$(GIT_ROOT)/tools/build/bootstrap package $(PKG_PRODUCT) $(VERSION) $(SIGNED_ARG) --build-all

_prepare_syncdet:
	$(eval SRC := $(shell ls $(GIT_ROOT)/out.gradle/packages/$(PS_PREFIX)*$(PS_EXT)))
	@if [ $(words $(SRC)) -ne 1 ]; then \
		echo "\033[31merror: \033[0mAmbiguous packages: $(SRC)"; \
		exit 1; \
	fi

	$(eval DEST := $(GIT_ROOT)/system-tests/syncdet/$(PS_PREFIX).$(PS_EXT))
	cp $(SRC) $(DEST)
	chmod 0700 $(DEST)

_prepare_syncdet_client:
	make _prepare_syncdet PS_PREFIX=AeroFSInstall PS_EXT=exe
	make _prepare_syncdet PS_PREFIX=aerofs-osx PS_EXT=zip
	make _prepare_syncdet PS_PREFIX=aerofs-installer PS_EXT=tgz

_prepare_syncdet_team_server:
	make _prepare_syncdet PS_PREFIX=AeroFSTeamServerInstall PS_EXT=exe
	make _prepare_syncdet PS_PREFIX=aerofsts-osx PS_EXT=zip
	make _prepare_syncdet PS_PREFIX=aerofsts-installer PS_EXT=tgz

_prepare_syncdet_storage_agent:
	make _prepare_syncdet PS_PREFIX=aerofs-storage-agent-installer PS_EXT=tgz

_syncdet:
	$(eval TEAM_CITY_ARG := $(shell [ $(TEAM_CITY) = true ] && echo "--team-city" || echo ""))
	$(SYNCDET_EXECUTABLE) $(ARG) \
			$(TEAM_CITY_ARG) \
			--purge-log \
			$(SYNCDET_ARGS) \
			--config=$(SYNCDET_CONFIG) \
			--case-timeout=$(SYNCDET_CASE_TIMEOUT) \
			--sync-timeout=$(SYNCDET_SYNC_TIMEOUT) \
			$(GIT_ROOT)/system-tests/syncdet \
			$(GIT_ROOT)/src/python-lib/./aerofs_common \
			$(GIT_ROOT)/src/python-lib/./aerofs_sp \
			$(GIT_ROOT)/src/python-lib/./aerofs_ritual \
			$(GIT_ROOT)/resource/templates/client/linux/storage-agent/./setup_storage_agent.py

_test_python:
	$(GIT_ROOT)/env/bin/pip install -r $(GIT_ROOT)/src/$(PYPROJECT)/requirements.txt
	$(GIT_ROOT)/env/bin/pip install -e $(GIT_ROOT)/src/$(PYPROJECT)

	(cd $(GIT_ROOT)/src/$(PYPROJECT) && $(GIT_ROOT)/env/bin/python test_all.py )


out.shell/protobuf-rpc/gen_rpc_java/protoc-gen-rpc-java: tools/protobuf.plugins/rpc_plugins.pro $(wildcard tools/protobuf.plugins/gen_rpc_*/*)
	mkdir -p $(GIT_ROOT)/out.shell/protobuf-rpc
	( \
		cd $(GIT_ROOT)/out.shell/protobuf-rpc && \
		rm -rf * && \
		qmake ../../tools/protobuf.plugins/rpc_plugins.pro && \
		make \
	)