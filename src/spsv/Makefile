IMAGE_NAME = aerofs/servlets

WEB_INF_DIR = buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF
LIB_DIR = $(WEB_INF_DIR)/lib
CONF_PROP = $(WEB_INF_DIR)/classes/configuration.properties
ZELDA_PROP = buildroot/etc/aerofs/zelda.properties
FIXES = \
	../lib/src/com/aerofs/lib/LibParam.java \
	../base/src/main/java/com/aerofs/base/BaseParam.java \
	../libservlet/src/com/aerofs/servlets/lib/AsyncEmailSender.java \
	../libservlet/src/com/aerofs/sp/server/lib/session/RequestRemoteAddress.java \
	../lib/src/com/aerofs/lib/properties/ServerConfigurationLoader.java \
	src/com/aerofs/sp/server/listeners/SPLifecycleListener.java

image: .fix-source .gradle .unfix-source $(ZELDA_PROP) $(CONF_PROP)
	docker build -t $(IMAGE_NAME) .

DIRS =  sp \
	log-collection \
	identity \
	probe \
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

$(ZELDA_PROP): ../../puppetmaster/modules/public-zelda-creds/files/zelda.properties
	mkdir -p $(@D)
	cp $< $@

.fix-source: $(FIXES)
$(FIXES): .force
	patch $(abspath $@) < $(@F).docker.patch

.force:

UNFIXES = $(FIXES:%=unfix-%)
.unfix-source: $(UNFIXES)
$(UNFIXES):
	patch -R $(abspath $(@:unfix-%=%)) < $(notdir $(@:unfix-%=%)).docker.patch

