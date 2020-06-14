import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A scanner and parser for requests.
 */

class ReqTokenizer {
    ReqTokenizer() { ; }
    
    /**
     * Parses requests.
     */
    Token getToken(String req) {
	StringTokenizer sTokenizer = new StringTokenizer(req);
	if (!(sTokenizer.hasMoreTokens()))
	    return null;
	String firstToken = sTokenizer.nextToken();
	if (firstToken.equals("JOIN")) {
	    if (sTokenizer.hasMoreTokens()) {
			return new JoinToken(req, Integer.parseInt(sTokenizer.nextToken()));
		}
	    else
		return null;
	}
	if (firstToken.equals("DETAILS")) {
	    List<Integer> portList = new ArrayList<>();
	    while (sTokenizer.hasMoreTokens())
			portList.add(Integer.parseInt(sTokenizer.nextToken()));
	    return new DetailsToken(req, portList);
	}
	if (firstToken.equals("VOTE_OPTIONS")) {
	    List<String> options = new ArrayList<>();
	    while (sTokenizer.hasMoreTokens())
			options.add(sTokenizer.nextToken());
	    return new VoteOptionsToken(req, options);
	}
	if (firstToken.equals("OUTCOME")) {
		String outcome = sTokenizer.nextToken();
		String involvedPorts = "";

		while (sTokenizer.hasMoreTokens()) {
			involvedPorts += " " + sTokenizer.nextToken();
		}
		return new OutcomeToken(req, outcome, involvedPorts);
	}

	if (firstToken.equals("VOTE")) {
		List<Vote> voteList = new ArrayList<>();
		while(sTokenizer.hasMoreTokens()) {
			int participantPort = Integer.parseInt(sTokenizer.nextToken());
			String selectedVote = sTokenizer.nextToken();
			voteList.add(new Vote(participantPort,selectedVote));
		}

		return new VoteToken(req, voteList);
	}

	return null; // Ignore request..
    }
}

/** 
 * The Token Prototype.
 */
abstract class Token {
    String _req;
}

/**
 * Syntax: JOIN &lt;name&gt;
 */
class JoinToken extends Token {
    int _id;

    JoinToken(String req, int id) {
	this._req = req;
	this._id = id;
    }
}

class DetailsToken extends Token {

	List<Integer> _portList;

	DetailsToken(String req, List<Integer> portList) {
		this._req = req;
		this._portList = portList;
	}
}

class VoteOptionsToken extends Token {
    List<String> optionList;

	VoteOptionsToken(String req, List<String> optionList) {
		this._req = req;
		this.optionList = optionList;
    }
}

class OutcomeToken extends Token {
    String outcome, portsInvolved;

	OutcomeToken(String req, String outcome, String portsInvolved) {
		this._req = req;
		this.outcome = outcome;
		this.portsInvolved = portsInvolved;
    }
}

class VoteToken extends Token {

	List<Vote> vote;

	VoteToken(String req, List<Vote> vote) {
		this._req = req;
		this.vote = vote;
	}
}


