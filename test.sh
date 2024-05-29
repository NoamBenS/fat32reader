#!/bin/bash

# compile file
javac fat32_reader.java

# run file
java fat32_reader fat32.img < inputcommands.txt > output.txt