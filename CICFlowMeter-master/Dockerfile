FROM gradle:4.2.1-jdk8

ENV APP_HOME=/CICFlowMeter/
WORKDIR $APP_HOME

COPY  ./ /CICFlowMeter/

USER root 
EXPOSE 25333

RUN printf '%s\n' \
		'deb http://archive.debian.org/debian stretch main' \
		> /etc/apt/sources.list \
	&& printf '%s\n' \
		'Acquire::Check-Valid-Until "false";' \
		'Acquire::AllowInsecureRepositories "true";' \
		'APT::Get::AllowUnauthenticated "true";' \
		> /etc/apt/apt.conf.d/99archive \
	&& apt-get update -qq \
	&& apt-get install -y --no-install-recommends libpcap0.8 libpcap-dev \
	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

RUN mkdir -p /CICFlowMeter/data/daily

RUN chown -R gradle /CICFlowMeter              
RUN gradle build --stacktrace --no-daemon

CMD ["gradle","execute","--no-daemon"]