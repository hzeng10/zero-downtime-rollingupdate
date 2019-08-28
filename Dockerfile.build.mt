FROM maven:3.5.2-jdk-8-alpine
RUN mkdir /build

WORKDIR /build

CMD ["sh", "build.sh"]