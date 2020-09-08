#!/bin/sh
cp -f /etc/resolv.conf.override /etc/resolv.conf
java -jar /opt/livefeed/livefeed.jar
