package io.singularitynet.soundspleeter;

import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterBlockingStub;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Input;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Output;

public class Client {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: Client <audio_url> <vocals.wav> <accomp.wav>");
            System.exit(1);
        }

        Channel channel = ManagedChannelBuilder
            .forAddress("localhost", 1234)
            .usePlaintext()
            .build();
        SoundSpleeterBlockingStub stub = SoundSpleeterGrpc.newBlockingStub(channel);

        Input request = Input.newBuilder()
            .setAudioUrl(args[0])
            .build();
        Output response = stub.spleeter(request);
        bytesToFile(response.getVocals().toByteArray(), args[1]);
        bytesToFile(response.getAccomp().toByteArray(), args[2]);
    }

    private static void bytesToFile(byte[] bytes, String fileName) throws IOException, FileNotFoundException {
        FileOutputStream stream = new FileOutputStream(fileName);
        try {
            stream.write(bytes);
        } finally {
            stream.close();
        }
    }

}
