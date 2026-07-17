package xyz.jasenon.lab.mqtt.protocol.command.seq;

public interface SeqGenerator {

    String generate(byte[] payload);


}
