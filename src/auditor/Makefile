IMAGE_NAME = aerofs/auditor
ROOT = buildroot/opt/auditor

# PREDOCKER
image: $(ROOT)/auditor.yml
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/auditor/dist $(ROOT)
	docker build -t $(IMAGE_NAME) .

$(ROOT)/auditor.yml: src/dist/auditor.yml
	mkdir -p $(@D)
	sed -e 's/localhost/0.0.0.0/' $< > $@
