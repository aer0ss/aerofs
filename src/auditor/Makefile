IMAGE_NAME = aerofs/auditor
ROOT = buildroot/opt/auditor

# PREDOCKER
image: .fix-source .gradle .restore-source $(ROOT)/auditor.yml
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/auditor/dist $(ROOT)

$(ROOT)/auditor.yml: src/dist/auditor.yml
	mkdir -p $(@D)
	sed -e 's/localhost/0.0.0.0/' $< > $@

FIXES = $(abspath \
	../lib/src/com/aerofs/lib/configuration/ConfigurationUtils.java)

.fix-source:
	for i in $(FIXES); do patch $$i < $$(basename $$i).docker.patch; done

.restore-source:
	for i in $(FIXES); do patch -R $$i < $$(basename $$i).docker.patch; done
