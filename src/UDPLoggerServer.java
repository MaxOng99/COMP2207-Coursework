import java.io.*;
import java.net.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class UDPLoggerServer{

    private int port;
    private PrintStream ps;
    private long receiveTime;
    private String ack;
    private DatagramSocket socket;
    private DatagramPacket packet;

    public UDPLoggerServer (int portNum) throws Exception {
        ack = "ACK";
        port = portNum;
        socket = new DatagramSocket(port);
        ps = new PrintStream("logger_server_"+System.currentTimeMillis()+".log");

        while(true) {
            String msg = receiveMessage();
            sendAcknowledgement(packet.getAddress(), packet.getPort());
            logMessages(msg, receiveTime);
        }
    }

    public String receiveMessage() throws IOException {
        byte[] message = new byte[1024];
        packet = new DatagramPacket(message, message.length);
        socket.receive(packet);
        receiveTime = System.currentTimeMillis();
        return new String(packet.getData(), packet.getOffset(), packet.getLength());
    }

    public void sendAcknowledgement(InetAddress address, int port) throws IOException {
        byte[] message = ack.getBytes();
        DatagramPacket packet = new DatagramPacket(message, message.length, address, port);
        socket.send(packet);
    }

    public void logMessages(String message, long receiveTime) {
        ps.println(receiveTime + " " + message);
    }

    public int getPort() {
        return port;
    }

    public static void main(String[] args) throws Exception {
        int port =Integer.parseInt(args[0]);
        new UDPLoggerServer(port);
    }
}
