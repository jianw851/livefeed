#!/bin/sh
chmod -rxw /etc/resolv.conf
cp -f /etc/resolv.conf.override /etc/resolv.conf
java -jar /opt/livefeed/livefeed.jar
