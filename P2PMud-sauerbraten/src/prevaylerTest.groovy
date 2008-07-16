import PrevaylerJr
import PrevaylerJr.Command

public static class AddItem implements Command, Serializable {
	int i;
	
	private static final long serialVersionUID = 0L;

	def executeOn(Object sys) {
		sys[sys.size()] = "duh: $i"
	}
}

prev = new PrevaylerJr([:], "/tmp/prevayler")
println "old value..."
prev.executeQuery({system -> 
	for (k in system) {
		println k
	}
} as Command)
println "adding 10 items"
for (i = 0; i < 10; i++) {
	prev.executeTransaction(new AddItem(i: i))
}
