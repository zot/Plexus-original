package p2pmud;

import rice.p2p.commonapi.Message;

public class P2PMudMessage implements Message {
	public P2PMudCommand cmd;

	private static final long serialVersionUID = 1L;

	public P2PMudMessage(P2PMudCommand cmd) {
		this.cmd = cmd;
	}
	public int getPriority() {
		return Message.LOW_PRIORITY;
	}
}
