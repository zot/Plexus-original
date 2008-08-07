package p2pmud;

import java.util.Collection;

import rice.p2p.commonapi.Id;
import rice.p2p.scribe.Topic;

public interface P2PMudCommandHandler {
	void handleCommand(Id id, Topic topic, P2PMudCommand cmd);
}
