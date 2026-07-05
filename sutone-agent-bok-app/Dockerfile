# 镜像库；https://github.com/hhhhaaaaaad/docker-image-pusher
FROM registry.cn-hangzhou.aliyuncs.com/xfg-studio/openjdk:17-jdk-slim

# 作者
MAINTAINER sutonesuke

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/sutone-agent-bok-app.jar /sutone-agent-bok-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /sutone-agent-bok-app.jar $PARAMS"]