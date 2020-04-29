FROM singularitynet/snet-local-env:3.0.0

RUN apt-get update
RUN apt-get install -y ffmpeg

RUN git clone https://github.com/singnet/dnn-model-services.git
WORKDIR ./dnn-model-services/services/sound-spleeter

RUN sed -ie 's/spleeter/spleeter==1.4.9/' ./requirements.txt
RUN pip3 install -r requirements.txt
RUN sh buildproto.sh

COPY ./install_spleeter.sh .
RUN sh install_spleeter.sh

CMD start_environment.sh && python3 ./run_service.py
