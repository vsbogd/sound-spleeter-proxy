package io.singularitylab.soundspleeter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterBlockingStub;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Input;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Output;

public class Client {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: Client <proxy_url> <audio_file> <vocals.wav> <accomp.wav>");
            System.exit(1);
        }

        URL proxyUrl = new URL(args[0]);
        boolean useTsl = proxyUrl.getProtocol().equals("https");
        String proxyHost = proxyUrl.getHost();
        int proxyPort = proxyUrl.getPort();
        File audioFile = new File(args[1]);
        String vocalsWav = args[2];
        String accompWav = args[3];

        ManagedChannelBuilder builder = ManagedChannelBuilder
            .forAddress(proxyHost, proxyPort)
            .maxInboundMessageSize(1 << 24);
        if (useTsl) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        ManagedChannel channel = builder.build();
        try {
            SoundSpleeterBlockingStub stub = SoundSpleeterGrpc.newBlockingStub(channel);

            Input request = Input.newBuilder()
                .setAudio(ByteString.copyFrom(Files.readAllBytes(audioFile.toPath())))
                .build();
            Output response = stub.spleeter(request);
            bytesToFile(response.getVocals().toByteArray(), vocalsWav);
            bytesToFile(response.getAccomp().toByteArray(), accompWav);
        } finally {
            channel.shutdownNow();
        }
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
