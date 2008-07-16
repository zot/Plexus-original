package p2pmud;

import rice.p2p.scribe.ScribeContent;

public class P2PMudScribeContent implements ScribeContent {
	public String message;

	private static final long serialVersionUID = 1L;

	public P2PMudScribeContent(String msg) {
		message = msg;
	}
	public String toString() {
		return "P2PMudCOntent: " + message;
	}
}
