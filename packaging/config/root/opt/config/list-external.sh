#!/bin/bash
grep {{.*}} templates/*.tmplt | sed 's/.*\({{.*}}\).*/\1/' | sort | uniq | awk -F' ' '{print $2}'
