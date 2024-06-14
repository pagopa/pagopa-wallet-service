FROM mcr.microsoft.com/azure-cli:2.47.0

WORKDIR /usr/queues/script

COPY ./create-queues.sh ./create-queues.sh

RUN ["chmod" ,"+x" ,"create-queues.sh"]

CMD ["sh","create-queues.sh"]