IMAGE_NAME = aerofs/servlets

image: base subdirs

base:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/spsv/dist buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF/lib
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
