Build:
```
mvn package
```

Run server and proxy in local environment:
```sh
docker build -t sound-spleeter .
docker run -d --rm --name sound-spleeter -p 5002:5002 -p 8545:8545 -p 8000:8000 -ti sound-spleeter
java -jar ./target/sound-spleeter-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar local
```

Run client:
```
wget https://github.com/deezer/spleeter/raw/master/audio_example.mp3
java -cp ./target/sound-spleeter-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar \
    io.singularitylab.soundspleeter.Client \
    http://localhost:1234 audio_example.mp3 vocals.wav accomp.wav
```
