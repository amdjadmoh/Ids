FROM gradle:4.2.1-jdk8-alpine

ENV APP_HOME=/CICFlowMeter/
ENV MODEL_HOST=model
ENV MODEL_PORT=5000
WORKDIR $APP_HOME

COPY  ./ /CICFlowMeter/

USER root 
EXPOSE 25333

RUN chown -R gradle /CICFlowMeter              
RUN ./gradlew build --stacktrace

CMD ["sh","-c","./get_interface.sh && ./gradlew execute"]
