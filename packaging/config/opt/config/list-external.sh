#!/bin/bash
grep {{.*}} templates/*.properties | sed 's/.*\({{.*}}\).*/\1/' | sort | uniq | awk -F' ' '{print $2}'
