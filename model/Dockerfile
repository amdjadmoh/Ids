FROM python:3.8-slim-bullseye

WORKDIR /app

COPY ./ /app/

RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends curl gcc g++ \
    && rm -rf /var/lib/apt/lists/*
RUN pip install -r ./requirements.txt


EXPOSE 7777
EXPOSE 5000

ENV TERM=linux
ENV TERMINFO=/usr/share/terminfo

CMD ["python","app.py","--host=0.0.0.0"]
