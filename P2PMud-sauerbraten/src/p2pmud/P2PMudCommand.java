package p2pmud;

import java.io.Serializable;

import rice.p2p.commonapi.Id;

public class P2PMudCommand implements Serializable {
	public Id from;
	public String msgs[];

	private static final long serialVersionUID = 1L;

	public P2PMudCommand(Id id, String only) {
		this.from = id;
		msgs = new String[]{only};
	}
	public P2PMudCommand(Id id, String[] cmds) {
		this.from = id;
		msgs = cmds;
	}
	public String toString() {
		StringBuffer buf = new StringBuffer("P2PMudCommand: ");
		boolean first = true;

		for (String m: msgs) {
			if (first) {
				first = false;
			} else {
				buf.append(", ");
			}
			buf.append(m);
		}
		return super.toString();
	}
}
