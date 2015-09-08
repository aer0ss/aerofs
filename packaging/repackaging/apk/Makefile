BASE_DIR := $(shell pwd)/../base

$(BASE_DIR)/x86_64: dmg-hfsplus gcab msitools
	docker build -t aerofs/apkg .

	rm -f $(BASE_DIR)/x86_64/*
	docker run --rm -it \
			-v $(BASE_DIR):/home/build/packages/main \
			--dns 8.8.8.8 \
			aerofs/apkg \
			/bin/bash -c \
		"cd /aports/main/dmg-hfsplus && abuild checksum && abuild -r && \
		cd /aports/main/gcab && abuild checksum && abuild -r && \
		cd /aports/main/msitools && abuild checksum && abuild -r && \
		abuild-sign -k /home/build/.abuild/*.rsa /home/build/packages/main/x86_64/APKINDEX.tar.gz"
