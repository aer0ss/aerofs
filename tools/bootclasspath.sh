#!/bin/sh

find $(update-alternatives --list java | grep java-6 | sed 's/\/bin\/java//') -name rt.jar

