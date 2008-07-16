package p2pmud;

import rice.p2p.commonapi.Id;
import rice.p2p.past.ContentHashPastContent;

public class P2PMudPastContent extends ContentHashPastContent {
	public String content;

	private static final long serialVersionUID = 1L;

	public P2PMudPastContent(Id id, String content) {
		super(id);
		this.content = content;
	}
	public String toString() {
		return "P2PMud PAST content: " + content;
	}
}
