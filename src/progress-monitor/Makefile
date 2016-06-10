DEST=$(abspath ../../resource/updater)

all: osx

osx: cleanosx
	gcc ./osx/progress-monitor.m -framework Cocoa -x objective-c -o $(DEST)/osx/progressmonitor

cleanosx:
	rm -rf $(DEST)/osx/progressmonitor
