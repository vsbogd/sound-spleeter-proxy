Run local environment:
```sh
docker build -t sound-spleeter .
docker run --rm --name sound-spleeter -p 5002:5002 -p 8545:8545 -p 8000:8000 -ti sound-spleeter
mvn package exec:java -Dexec.mainClass=io.singularitynet.soundspleeter.App -Dexec.args="local.properties"
```
