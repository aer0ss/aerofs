IMAGE_NAME = aerofs/servlets

FLAG_FILE = buildroot/etc/aerofs/private-deployment-flag
WEB_INF_DIR = buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF
LIB_DIR = $(WEB_INF_DIR)/lib
CONF_PROP = $(WEB_INF_DIR)/classes/configuration.properties
FIXES = \
	../lib/src/com/aerofs/lib/LibParam.java \
	../base/src/main/java/com/aerofs/base/BaseParam.java \
	../libservlet/src/com/aerofs/servlets/lib/AsyncEmailSender.java \
	../libservlet/src/com/aerofs/sp/server/lib/session/RequestRemoteAddress.java \
	../lib/src/com/aerofs/lib/properties/Configuration.java \
	src/com/aerofs/sp/server/listeners/SPLifecycleListener.java

image: .fix-source .gradle .unfix-source $(CONF_PROP) $(FLAG_FILE)
	docker build -t $(IMAGE_NAME) .

DIRS =  sp \
	log-collection \
	identity \
	verification

subdirs: $(DIRS:%=dockerize-%)
dockerize-%:
	@echo "========================================"
	@echo "  Dockerizing $*"
	@echo "========================================"
	$(MAKE) -C resources/$* image

all: image subdirs

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/spsv/dist $(LIB_DIR)

$(CONF_PROP):
	mkdir -p $(@D)
	echo "config.loader.is_private_deployment=true" > $@

$(FLAG_FILE):
	mkdir -p $(@D)
	touch $@

.fix-source: $(FIXES)
$(FIXES): .force
	patch $(abspath $@) < $(@F).docker.patch

.force:

UNFIXES = $(FIXES:%=unfix-%)
.unfix-source: $(UNFIXES)
$(UNFIXES):
	patch -R $(abspath $(@:unfix-%=%)) < $(notdir $(@:unfix-%=%)).docker.patch

