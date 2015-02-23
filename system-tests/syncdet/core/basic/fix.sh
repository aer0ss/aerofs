#!/bin/bash

for f in *
do
    dos2unix "$f"
done
