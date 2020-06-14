import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.util.*;

public class Participant {

    private int coordinatorPort;
    private int loggerServerPort;
    private int thisParticipantPort;
    private int timeOut;
    private String outcome;
    private ReqTokenizer tokenizer;
    private List<Integer> participantPorts;
    private List<String> options;
    private List<Socket> participants;
    private ParticipantLogger logger;
    private Socket coordinatorSocket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerSocket serverSocket;
    private volatile List<Vote> newVotes;
    private volatile List<Vote> currentVotes;
    private volatile boolean sendingVotes = true;
    private volatile boolean receiverSetUpDone = false;
    private volatile boolean senderSetUpDone = false;
    private volatile List<Integer> crashedParticipants = Collections.synchronizedList(new ArrayList<>());
    private final Object monitor = new Object();
    private final Object setUpConnectionMonitor = new Object();

    public Participant(int coordinatorPort, int loggerServerPort, int thisParticipantPort, int timeOut) throws Exception {
        this.coordinatorPort = coordinatorPort;
        this.loggerServerPort = loggerServerPort;
        this.thisParticipantPort = thisParticipantPort;
        this.timeOut = timeOut;
        ParticipantLogger.initLogger(loggerServerPort, thisParticipantPort, timeOut);
        this.logger = ParticipantLogger.getLogger();
        tokenizer = new ReqTokenizer();
        participants = new ArrayList<>();
        serverSocket = new ServerSocket(thisParticipantPort);
        newVotes = Collections.synchronizedList(new ArrayList<>());
        currentVotes = Collections.synchronizedList(new ArrayList<>());
    }

    public void joinCoordinator() throws InterruptedException {
        boolean joined = false;
        while (!joined) {
            try{
                coordinatorSocket = new Socket("localhost", coordinatorPort);
                in = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(coordinatorSocket.getOutputStream()), true);
                joined = true;

                String joinToken = "JOIN " + thisParticipantPort;
                out.println(joinToken);
                logger.messageSent(coordinatorPort, joinToken);
                logger.joinSent(coordinatorPort);
            }
            catch (IOException e) {
                Thread.sleep(1000);
            }
        }
    }

    public void receiveDetails() throws IOException {
        String details = in.readLine();
        logger.messageReceived(coordinatorPort, details);
        Token token = tokenizer.getToken(details);
        if (token instanceof DetailsToken) {
            participantPorts = ((DetailsToken) token)._portList;
            logger.detailsReceived(participantPorts);
        }
    }

    public void receiveVoteOptions() throws IOException {
        String voteOptions = in.readLine();
        logger.messageReceived(coordinatorPort, voteOptions);
        Token token = tokenizer.getToken(voteOptions);
        if (token instanceof VoteOptionsToken) {
            options = ((VoteOptionsToken) token).optionList;
            logger.voteOptionsReceived(options);
        }
    }

    public void decide() {
        String[] sortedVotes = lexicalGraphicalSort(currentVotes);
        outcome = getMajority(sortedVotes);
        participantPorts.add(thisParticipantPort);
        logger.outcomeDecided(outcome, participantPorts);
    }

    public void informOutcome() {
        String outcomeToken = "OUTCOME " + outcome;

        for (Integer port: participantPorts) {
            outcomeToken += " " + port;
        }
        out.println(outcomeToken);
        logger.messageSent(coordinatorPort, outcomeToken);
        logger.outcomeNotified(outcome, participantPorts);
    }

    public String[] lexicalGraphicalSort(List<Vote> currentVotes) {
        String[] votes = new String[currentVotes.size()];
        for (int i = 0; i < votes.length; i++) {
            votes[i] = currentVotes.get(i).getVote();
        }
        for (int i = 0; i < votes.length; i++) {
            for (int j = 1 + 1; j < votes.length; j++) {
                if (votes[i].compareTo(votes[j]) > 0) {
                    String temp = votes[i];
                    votes[i] = votes[j];
                    votes[j] = temp;
                }
            }
        }
        return votes;
    }

    public String getMajority(String[] sortedVotes) {
        Map<String, Integer> map = new HashMap<>();

        for (String vote : sortedVotes) {
            String selectedVote = vote;
            Integer val = map.get(selectedVote);
            map.put(selectedVote, val == null ? 1 : val + 1);
        }

        Map.Entry<String, Integer> max = null;

        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (max == null || e.getValue() > max.getValue())
                max = e;
        }
        return max.getKey();
    }

    public class Receiver extends Thread {

        private Map<Integer, BufferedReader> idInputStreamMap;
        private Map<Integer, Socket> idSocketMap;
        private Map<Integer, Integer> portMap;
        private int round = 1;

        public Receiver() {
            idInputStreamMap = new HashMap<>();
            idSocketMap = new HashMap<>();
            portMap = new HashMap<>();
        }

        public void startListening() throws IOException {
            logger.startedListening();
            for (int i = 0; i < participantPorts.size(); i++) {
                Socket otherParticipant = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(otherParticipant.getInputStream()));
                String joinMsg = in.readLine();
                int participantId = Integer.parseInt(joinMsg.split(" ")[1]);
                otherParticipant.setSoTimeout(timeOut);
                portMap.put(otherParticipant.getPort(), participantId);
                idInputStreamMap.put(otherParticipant.getPort(), in);
                idSocketMap.put(otherParticipant.getPort(), otherParticipant);
                logger.connectionAccepted(otherParticipant.getPort());
            }
            receiverSetUpDone = true;
        }

        public void receiveVotes() throws IOException, InterruptedException {
            synchronized (setUpConnectionMonitor) {
                while (!senderSetUpDone) {
                    setUpConnectionMonitor.wait();
                }
                setUpConnectionMonitor.notify();
            }
            for (int i = 0; i < participantPorts.size()+1; i++) {
                receiveRoundNVotes();
            }
        }

        public void receiveRoundNVotes() throws IOException, InterruptedException {
            synchronized (monitor) {
                while (sendingVotes) {
                    monitor.wait();
                }
                List<Integer> portToRemove = new ArrayList<>();
                for (Map.Entry<Integer, BufferedReader> entry: idInputStreamMap.entrySet()) {
                    int participantPort = entry.getKey();
                    try {
                        String msg = entry.getValue().readLine();
                        Token token = tokenizer.getToken(msg);
                        logger.messageReceived(participantPort, msg);
                        if (token instanceof VoteToken) {
                            List<Vote> votes = ((VoteToken) token).vote;
                            List<Vote> temp = new ArrayList<>();
                            for (Vote vote: votes) {
                                if (isNewVote(vote)) {
                                    temp.add(vote);
                                }
                            }
                            logger.votesReceived(portMap.get(participantPort), votes);
                            newVotes.addAll(temp);
                        }
                    } catch (SocketTimeoutException e) {
                        crashedParticipants.add(portMap.get(participantPort));
                        portToRemove.add(participantPort);
                        logger.participantCrashed(portMap.get(participantPort));
                    }
                }
                removeCrashedParticipants(portToRemove);
                currentVotes.addAll(newVotes);
                logger.endRound(round);
                round++;
                sendingVotes = true;
                monitor.notify();
            }
        }

        public void removeCrashedParticipants(List<Integer> portToRemove) {
            for (Integer port: portToRemove) {
                idInputStreamMap.remove(port);
                idSocketMap.remove(port);
            }
        }

        public boolean isNewVote(Vote voteToCheck) {
            boolean newVote = true;
            if (currentVotes.size() > 1) {
                for (Vote vote: currentVotes) {
                    if (vote.getParticipantPort() == voteToCheck.getParticipantPort() && vote.getVote().equals(voteToCheck.getVote())) {
                        newVote = false;
                        break;
                    }
                }
            }
            return newVote;
        }

        public void run() {
            try {
                startListening();
                receiveVotes();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public class Sender extends Thread {

        private Map<Integer, PrintWriter> idOutputStreamMap;
        private Map<Integer, Socket> idSocketMap;
        private int round = 1;

        public Sender() {
            idOutputStreamMap = new HashMap<>();
            idSocketMap = new HashMap<>();
        }

        public void createConnections() {
            for (Integer port: participantPorts) {
                try {
                    Socket socket = new Socket("localhost", port);
                    logger.connectionEstablished(socket.getPort());
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                    String joinMsg = "JOIN " + thisParticipantPort;
                    out.println(joinMsg);
                    idOutputStreamMap.put(port, out);
                    idSocketMap.put(port, socket);
                    participants.add(socket);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            senderSetUpDone = true;
        }

        private void sendRound1Votes() throws InterruptedException {
            synchronized (monitor) {
                while(!sendingVotes) {
                    monitor.wait();
                }
                logger.beginRound(round);
                String selectedOption = options.get(new Random().nextInt(options.size()));
                String round1Msg = "VOTE " + thisParticipantPort + " " + selectedOption;
                Vote vote = new Vote(thisParticipantPort, selectedOption);
                currentVotes.add(vote);

                for (Map.Entry<Integer, PrintWriter> entry: idOutputStreamMap.entrySet()) {
                    int port = entry.getKey();
                    entry.getValue().println(round1Msg);
                    logger.messageSent(port, round1Msg);
                    logger.votesSent(port, currentVotes);
                }
                sendingVotes = false;
                monitor.notify();
            }
        }

        private void sendRoundNVotes() throws InterruptedException, IOException {
            synchronized (monitor) {
                while(!sendingVotes) {
                    monitor.wait();
                }

                removeCrashedParticipants();
                round++;
                logger.beginRound(round);

                for (Map.Entry<Integer, PrintWriter> entry: idOutputStreamMap.entrySet()) {
                    String msg = "VOTE";
                    int destinationPort = entry.getKey();
                    for (Vote vote: newVotes) {
                        msg += " " + vote.getParticipantPort() + " " + vote.getVote();
                    }
                    entry.getValue().println(msg);
                    logger.messageSent(destinationPort, msg);
                    logger.votesSent(destinationPort, newVotes);
                }
                crashedParticipants.clear();
                newVotes.clear();
                sendingVotes = false;
                monitor.notify();
            }
        }

        public void sendVotes() throws InterruptedException, IOException {
            synchronized (setUpConnectionMonitor) {
                while (!receiverSetUpDone) {
                    setUpConnectionMonitor.wait();
                }
                setUpConnectionMonitor.notify();
            }
            sendRound1Votes();
            for (int i = 0; i < participantPorts.size(); i++) {
                sendRoundNVotes();
            }
        }

        private void removeCrashedParticipants() throws IOException{
            for (Integer port: crashedParticipants) {
                idOutputStreamMap.remove(port);
                idSocketMap.get(port).close();
            }
        }

        public void run() {
            createConnections();
            try {
                sendVotes();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        int cPort = Integer.parseInt(args[0]);
        int loggerPort = Integer.parseInt(args[1]);
        int participantPort = Integer.parseInt(args[2]);
        int timeOut = Integer.parseInt(args[3]);

        Participant participant = new Participant(cPort, loggerPort, participantPort, timeOut);
        participant.joinCoordinator();
        participant.receiveDetails();
        participant.receiveVoteOptions();

        Sender sender = participant.new Sender();
        Receiver receiver = participant.new Receiver();
        receiver.start();
        sender.start();

        sender.join();
        receiver.join();
        participant.decide();
        participant.informOutcome();
    }
}