
import java.net.ServerSocket;
import java.net.Socket;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class Coordinator
{
    private int coordinatorPort;
    private int loggerServerPort;
    private int maxParticipants;
    private int _numOfClients;
    private int timeOut;
    private List<String> options;
    private volatile List<Integer> participantIds;
    private ServerSocket listener;
    private CoordinatorLogger logger;
    private List<CoordinatorThread> threads;
    private ReqTokenizer tokenizer;
    private List<String> outcomeList;


    public Coordinator(int coordinatorPort, int loggerServerPort, int maxParticipants, int timeOut, String options) throws Exception {
        this.coordinatorPort = coordinatorPort;
        this.loggerServerPort = loggerServerPort;
        this.maxParticipants = maxParticipants;
        this._numOfClients = 0;
        this.timeOut = timeOut;
        this.options = convertToList(options);
        this.outcomeList = Collections.synchronizedList(new ArrayList<>());
        participantIds = Collections.synchronizedList(new ArrayList<>());
        CoordinatorLogger.initLogger(loggerServerPort, coordinatorPort, timeOut);
        logger = CoordinatorLogger.getLogger();
        tokenizer = new ReqTokenizer();
        threads = new ArrayList<>();
    }

    public void startListening() throws IOException {
        listener = new ServerSocket(coordinatorPort);
        logger.startedListening(coordinatorPort);
        while (!fullCapacity()) {
            Socket client = listener.accept();
            logger.connectionAccepted(client.getPort());
            _numOfClients++;
            new CoordinatorThread(client).start();
        }
    }

    public List<String> convertToList(String optionList) {
        List<String> list = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(optionList);
        while (tokenizer.hasMoreTokens()) {
            list.add(tokenizer.nextToken());
        }
        return list;
    }

    public boolean fullCapacity() {
        if (_numOfClients >= maxParticipants)
            return true;
        else
            return false;
    }

    /**
     * For each client we create a thread that handles
     * all i/o with that client.
     */
    private class CoordinatorThread extends Thread {

        private int id;
        private int remotePort;
        private int localPort;
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;

        public CoordinatorThread(Socket client) throws IOException {
            clientSocket = client;
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
            remotePort = client.getPort();
        }

        public void receiveJoin() throws IOException {
            try {
                String msg = in.readLine();
                logger.messageReceived(remotePort, msg);
                Token token = tokenizer.getToken(msg);
                if (!(token instanceof JoinToken)) {
                    clientSocket.close();
                    return;
                }
                id = ((JoinToken) token)._id;
                participantIds.add(id);
                logger.joinReceived(id);
            } catch (SocketException | SocketTimeoutException e2) {
                System.exit(0);
            }
        }
        public void sendDetails() {
            String msg = "DETAILS";
            List<Integer> participantPorts = new ArrayList<>();
            List<Integer> temp = new ArrayList<>(participantIds);

            for (Integer currentId: temp) {
                if (currentId != id) {
                    msg += " " + currentId;
                    participantPorts.add(currentId);
                }
            }
            out.println(msg);
            logger.messageSent(remotePort, msg);
            logger.detailsSent(id, participantPorts);
        }

        public void sendVoteOptions() {
            String msg = "VOTE_OPTIONS";
            for (String option: options) {
                msg += " " + option;
            }
            out.println(msg);
            logger.messageSent(remotePort, msg);
            logger.voteOptionsSent(id, options);
        }

        public void receiveOutcome() throws IOException {
            try {
                String msg = in.readLine();
                logger.messageReceived(remotePort, msg);
                Token token = tokenizer.getToken(msg);
                if (token instanceof OutcomeToken) {
                    String outcome = ((OutcomeToken) token).outcome;
                    outcomeList.add(outcome);
                    logger.outcomeReceived(id, outcome);
                }
            }
            catch (SocketException e) {
                logger.participantCrashed(id);
            }
        }

        public void run() {
            try {
                receiveJoin();
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (true) {
                if (participantIds.size() == maxParticipants) {
                    sendDetails();
                    sendVoteOptions();
                    try {
                        receiveOutcome();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        int coordinatorPort = Integer.parseInt(args[0]);
        int loggerServerPort = Integer.parseInt(args[1]);
        int maxParticipants = Integer.parseInt(args[2]);
        int timeOut = Integer.parseInt(args[3]);
        String options = "";

        for (int i = 4; i < args.length; i++) {
            options += " " + args[i];
        }

        Coordinator coordinator = new Coordinator(coordinatorPort, loggerServerPort, maxParticipants, timeOut, options);
        coordinator.startListening();
    }
}







