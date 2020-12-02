#!/bin/bash

spark-submit \
--verbose \
--master local[6] \
--class com.sparkfun.Main \
--files ./log4j.properties \
--conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:$(pwd)/log4j.properties \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.executor.heartbeatInterval=20 \
./target/scala-2.12/sparkfun.jar > test.out 2>&1&