DEST = ../../resource/updater
LINUX_BUILD = ./linux/build

osx: cleanosx
	gcc ./osx/progress-monitor.m -framework Cocoa -x objective-c -o $(DEST)/osx/progressmonitor

cleanosx:
	rm -rf $(DEST)/osx/progressmonitor

linux32:
	make -C ../../tools/build/linux32 COMMAND='make -C /mnt/repos/aerofs/src/progress-monitor linux32-emulated'

linux32-emulated: cleanlinux32
	mkdir -p $(LINUX_BUILD)/i386
	glib-compile-resources ./linux/assets.xml --sourcedir=./linux/assets --target=$(LINUX_BUILD)/i386/assets.c --generate-source
	gcc -Wall $(LINUX_BUILD)/i386/*.c ./linux/*.c `pkg-config --cflags --libs gtk+-3.0` -o $(DEST)/linux/progressmonitor-i386

cleanlinux32:
	rm -rf $(DEST)/linux/progressmonitor-i386
	rm -rf $(LINUX_BUILD)/i386

linux64:
	make -C ../../tools/build/linux64 COMMAND='make -C /mnt/repos/aerofs/src/progress-monitor linux64-emulated'

linux64-emulated: cleanlinux64
	mkdir -p $(LINUX_BUILD)/amd64
	glib-compile-resources ./linux/assets.xml --sourcedir=./linux/assets --target=./linux/build/amd64/assets.c --generate-source
	gcc -Wall $(LINUX_BUILD)/amd64/*.c ./linux/*.c `pkg-config --cflags --libs gtk+-3.0` -o $(DEST)/linux/progressmonitor-amd64

cleanlinux64:
	rm -rf $(DEST)/linux/progressmonitor-amd64
	rm -rf $(LINUX_BUILD)/amd64
