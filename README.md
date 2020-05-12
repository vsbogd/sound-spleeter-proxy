Build:
```
mvn package
```

Run server and proxy in local environment:
```sh
docker build -t sound-spleeter .
docker run -d --rm --name sound-spleeter -p 5002:5002 -p 8545:8545 -p 8000:8000 -ti sound-spleeter
java -cp ./target/sound-spleeter-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar \
    io.singularitynet.soundspleeter.App local
```

Run client:
```
AUDIO_URL=https://github.com/deezer/spleeter/raw/master/audio_example.mp3
java -cp ./target/sound-spleeter-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar \
    io.singularitynet.soundspleeter.Client \
    localhost 1234 ${AUDIO_URL} vocals.wav accomp.wav
```
