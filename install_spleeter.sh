#!/bin/sh

start_environment.sh

snet identity deployer
snet service \
        metadata-init \
        ./service/service_spec \
        "Sound Spleeter" \
        --group-name default_group \
        --endpoints http://127.0.0.1:8000 \
        --fixed-price 0.00000001
snet service publish example-org sound-spleeter -y
rm ./snetd_configs/*
cat >./snetd_configs/snetd.config.json << EOF
{
   "DAEMON_END_POINT": "0.0.0.0:8000",
   "IPFS_END_POINT": "http://localhost:5002",
   "BLOCKCHAIN_NETWORK_SELECTED": "local",
   "PASSTHROUGH_ENABLED": true,
   "PASSTHROUGH_ENDPOINT": "http://localhost:8003",
   "ORGANIZATION_ID": "example-org",
   "SERVICE_ID": "sound-spleeter",

   "log": {
       "level": "debug",
       "output": {
           "current_link": "./snetd-local.log",
           "file_pattern": "./snetd-local.%Y%m%d.log",
           "rotation_count": 0,
           "rotation_time_in_sec": 86400,
           "type": "file"
       }
   }
}
EOF
sed -ie 's/7003/8003/' ./service/__init__.py

snet identity caller
snet channel open-init --force --open-new-anyway \
    example-org default_group 1 +1000days -y
snet channel open-init --force --open-new-anyway \
    example-org default_group 1 +1000days -y

