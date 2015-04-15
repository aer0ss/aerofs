IMAGE_NAME = aerofs/ca
DST_DIR = buildroot/opt/ca-server
RSC_DIR = src/dist

FIXES = $(abspath \
	../lib/src/com/aerofs/lib/configuration/ConfigurationUtils.java)

# PREDOCKER
image: .fix-source .gradle $(DST_DIR)/ca.yml.tmplt .unfix-source
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/ca-server/dist $(DST_DIR)

$(DST_DIR)/%: $(RSC_DIR)/%
	mkdir -p $(@D)
	cp $< $@

.fix-source:
	for i in $(FIXES) ; do patch $$i < $$(basename $$i).docker.patch ; done

.unfix-source:
	for i in $(FIXES) ; do patch -R $$i < $$(basename $$i).docker.patch ; done

.PHONY: image .gradle .fix-source .unfix-source
