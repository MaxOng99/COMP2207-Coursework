import java.io.IOException;
import java.net.*;

public class UDPLoggerClient {

	private DatagramSocket socket;
	private int retransmissions;
	private final int loggerServerPort;
	private final int processId;
	private final int timeout;

	public UDPLoggerClient(int loggerServerPort, int processId, int timeout) throws Exception {
		this.loggerServerPort = loggerServerPort;
		this.processId = processId;
		this.timeout = timeout;
		socket = new DatagramSocket();
		socket.setSoTimeout(timeout);
		retransmissions = 0;
	}

	public void logToServer(String message) throws IOException {
		byte[] msg = message.getBytes();
		DatagramPacket writePacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), loggerServerPort);
		socket.send(writePacket);

		byte[] receiveMsg = new byte[1024];
		DatagramPacket readPacket = new DatagramPacket(receiveMsg, receiveMsg.length);
		try{
			socket.receive(readPacket);
		}
		catch (SocketTimeoutException e) {
			if (retransmissions == 3)
				throw new IOException();
			else
				logToServer(message);
		}
	}

	public int getLoggerServerPort() {
		return loggerServerPort;
	}

	public int getProcessId() {
		return processId;
	}
	
	public int getTimeout() {
		return timeout;
	}
}
