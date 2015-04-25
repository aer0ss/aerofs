IMAGE_NAME = aerofs/servlets

WEB_INF_DIR = buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF
LIB_DIR = $(WEB_INF_DIR)/lib
CONF_PROP = $(WEB_INF_DIR)/classes/configuration.properties
FIXES = $(abspath \
	../lib/src/com/aerofs/lib/LibParam.java \
	../base/src/main/java/com/aerofs/base/BaseParam.java \
	../libservlet/src/com/aerofs/servlets/lib/AsyncEmailSender.java \
	../libservlet/src/com/aerofs/sp/server/lib/session/RequestRemoteAddress.java \
	../lib/src/com/aerofs/lib/configuration/ConfigurationUtils.java \
	src/com/aerofs/sp/server/BifrostClient.java)

image: .fix-source .gradle .unfix-source $(CONF_PROP)
	docker build -t $(IMAGE_NAME) .

DIRS =  sp \
	log-collection \
	identity \
	probe \
	verification

subdirs: $(DIRS:%=dockerize-%)
dockerize-%:
	@echo "========================================"
	@echo "  Building Docker image $*"
	@echo "========================================"
	$(MAKE) -C resources/$* image

all: image subdirs

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/spsv/dist $(LIB_DIR)

$(CONF_PROP):
	mkdir -p $(@D)
	echo "config.loader.is_private_deployment=true" > $@

.fix-source:
	for i in $(FIXES) ; do patch $$i < $$(basename $$i).docker.patch; done

.unfix-source:
	for i in $(FIXES) ; do patch -R $$i < $$(basename $$i).docker.patch ; done

.PHONY: all image subdirs .gradle .fix-source .unfix-source $(DIRS:%=dockerize-%)
