package p2pmud;

import rice.p2p.commonapi.Id;
import rice.p2p.past.ContentHashPastContent;

public class P2PMudPastContent extends ContentHashPastContent {
	public P2PMudCommand cmd;

	private static final long serialVersionUID = 1L;

	public P2PMudPastContent(Id id, P2PMudCommand cmd) {
		super(id);
		this.cmd = cmd;
	}
	public String toString() {
		return "P2PMud PAST content: " + cmd;
	}
}
