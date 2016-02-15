IMAGE_NAME = aerofs/servlets
DST_DIR = buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF/lib

image: base subdirs

base:
	gradle --daemon dist
	rm -rf buildroot
	mkdir -p $(DST_DIR)
	ln ../../out.gradle/spsv/dist/* $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

DIRS =  sp \
	log-collection \
	openid \
	verification

subdirs: $(DIRS:%=dockerize-%)
dockerize-%:
	@echo "========================================"
	@echo "  Building Docker image $*"
	@echo "========================================"
	$(MAKE) -C resources/$* image

.PHONY: base image subdirs
