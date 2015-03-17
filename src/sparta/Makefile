IMAGE_NAME = aerofs/sparta
DST_DIR = buildroot/opt/sparta

FIXES = \
	src/com/aerofs/sp/sparta/Sparta.java \
	../lib/src/com/aerofs/lib/configuration/ConfigurationUtils.java

image: .fix-source .gradle .unfix-source $(DST_DIR)/logback.xml $(DST_DIR)/sparta.properties
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/sparta/dist $(DST_DIR)

.fix-source: $(FIXES)
$(FIXES): .force
	patch $(abspath $@) < $(@F).docker.patch

.force:

UNFIXES = $(FIXES:%=unfix-%)
.unfix-source: $(UNFIXES)
$(UNFIXES):
	patch -R $(abspath $(@:unfix-%=%)) < $(notdir $(@:unfix-%=%)).docker.patch


$(DST_DIR)/%: resources/%
	cp $< $@

$(DST_DIR)/sparta.properties: resources/sparta.properties
	sed -e 's|mysql://localhost/aerofs_sp|mysql://mysql.service/aerofs_sp|' \
		-e 's|http://localhost:8700/tokeninfo|http://bifrost.service:8700/tokeninfo|' \
		-e 's/127.0.0.1/0.0.0.0/' $< > $@
