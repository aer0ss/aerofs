IMAGE_NAME = aerofs/ca
DST_DIR = buildroot/opt/ca-server
RSC_DIR = src/dist

# PREDOCKER
image: .gradle $(DST_DIR)/ca.yml.tmplt
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/ca-server/dist $(DST_DIR)

$(DST_DIR)/%: $(RSC_DIR)/%
	mkdir -p $(@D)
	cp $< $@
