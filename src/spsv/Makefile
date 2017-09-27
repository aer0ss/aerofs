include ../../docker/Makefile.base

IMAGE_NAME = aerofs/servlets

DIST_SRC = $(GIT_ROOT)/out.gradle/spsv/dist/
DIST_DEST = buildroot/usr/share/tomcat7/webapps/ROOT/WEB-INF/lib/

SUBDIRS:=$(wildcard resources/*/.)

image: $(SUBDIRS)

build: buildroot
	docker build -t $(IMAGE_NAME) .

buildroot: dist
	mkdir -p $(DIST_DEST)
	rm -rf buildroot/usr/share/tomcat6
	$(RSYNC) --link-dest=$(DIST_SRC) $(DIST_SRC) $(DIST_DEST)

.PHONY: $(SUBDIRS)
$(SUBDIRS): build
	make -C $@ image
