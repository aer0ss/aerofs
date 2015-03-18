IMAGE_NAME = aerofs/ca
DST_DIR = buildroot/opt/ca-server
RSC_DIR = src/dist

FIXES = \
	../lib/src/com/aerofs/lib/configuration/ConfigurationUtils.java

# PREDOCKER
image: .fix-source .gradle $(DST_DIR)/ca.yml.tmplt .unfix-source
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/ca-server/dist $(DST_DIR)

$(DST_DIR)/%: $(RSC_DIR)/%
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
