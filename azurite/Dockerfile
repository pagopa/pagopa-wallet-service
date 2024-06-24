FROM mcr.microsoft.com/azure-cli:2.47.0@sha256:a9fc568020f977fbac633c1c8cb096029214d121e4a03531e74b6a685d161119

WORKDIR /usr/queues/script

COPY ./create-queues.sh ./create-queues.sh

RUN ["chmod" ,"+x" ,"create-queues.sh"]

CMD ["sh","create-queues.sh"]