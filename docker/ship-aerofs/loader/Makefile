include ../../Makefile.base

IMAGE_NAME = aerofs/loader

VERSION_SRC = ../../../out.gradle/packages/current.ver
VERSION_DEST = buildroot/tag

verify:
	docker run --rm \
		-v /var/run/docker.sock:/var/run/docker.sock \
		$(IMAGE_NAME) \
		verify $(IMAGE_NAME)
	$(call success,"- verify $(IMAGE_NAME)")

build:
	../../ship/vm/loader/build.sh

buildroot: $(VERSION_SRC)
	mkdir -p buildroot
	sed -e 's/.*=//' $< > $(VERSION_DEST)

$(VERSION_SRC):
	make -C ../../.. write_version
