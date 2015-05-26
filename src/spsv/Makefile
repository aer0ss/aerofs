IMAGE_NAME = aerofs/servlets

image:
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

all: image subdirs

.PHONY: all image subdirs
