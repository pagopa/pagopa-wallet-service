#!/bin/bash

echo "Analizing env variables"
queues=$(env | grep '^QUEUE_' | sed -n "s/^\(.*\)=.*/\1/p")
for queue in $queues
    do
        queue_value=$(eval "echo \$$queue")
        echo "Found queue into env -> $queue_value, creating queue"
        az storage queue create -n $queue_value --connection-string='DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;QueueEndpoint=http://storage:10001/devstoreaccount1'
    done