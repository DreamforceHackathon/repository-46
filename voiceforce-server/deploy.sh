#!/bin/bash

set -e -x

lein do cljsbuild clean, cljsbuild once
echo 'password: raspberry'
scp dist/voiceforce.js pi@192.168.1.66:~
