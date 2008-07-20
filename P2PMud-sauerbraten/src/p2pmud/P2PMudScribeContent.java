package p2pmud;

import rice.p2p.scribe.ScribeContent;

public class P2PMudScribeContent implements ScribeContent {
	public P2PMudCommand cmd;

	private static final long serialVersionUID = 1L;

	public P2PMudScribeContent(P2PMudCommand cmd) {
		this.cmd = cmd;
	}
	public String toString() {
		return "P2PMudUpdate: " + cmd;
	}
}
