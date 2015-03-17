IMAGE_NAME = aerofs/bifrost
DST_DIR = buildroot/opt/bifrost
RSC_DIR = resources

# PREDOCKER
image: .fix-source .gradle .restore-source $(DST_DIR)/bifrost.properties $(DST_DIR)/logback.xml
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/bifrost/dist $(DST_DIR)

FIXES = $(abspath \
	../lib/src/com/aerofs/lib/configuration/ServerConfigurationLoader.java \
	../base/src/main/java/com/aerofs/base/BaseParam.java)

.restore-source:
	for i in $(FIXES); do patch -R $$i < $$(basename $$i).docker.patch; done

$(DST_DIR)/%: $(RSC_DIR)/%
	mkdir -p $(@D)
	cp $< $@

.fix-source:
	for i in $(FIXES); do patch $$i < $$(basename $$i).docker.patch; done
