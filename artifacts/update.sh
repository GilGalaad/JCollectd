#!/bin/bash

cd -- "$(dirname -- "${BASH_SOURCE[0]}")" || exit 1
docker build --no-cache --target=out --output=out https://github.com/GilGalaad/JCollectd.git || exit 1
docker system prune -af

systemctl stop jcollectd
cp -f out/jcollectd.jar .
rm -f samples.db
rm -rf out
systemctl start jcollectd
