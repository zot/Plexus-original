import p2pmud.P2PMudCommand
public class PastryCmds extends Cmds {
	public PastryCmds(main) {
		super(main)
	}
	def invoke(cmdString) {
		super.invoke(cmdString)
	}
	def login(name, String... args) {
		main.sauer('echo', "echo ${name} has joined!")
		update(name, *args)
	}
	def update(name, String... args) {
		def id = main.ids[name]
		if (!id) {
			id = "p$main.id_index"
			main.ids[name] = id
			++main.id_index
			main.names[id] = name
			main.sauer('prep', "createplayer $id $name")
		}
		main.sauer("${id}.update", "tc_setinfo $id " + args.join(' '))
		main.dumpCommands()
	}
	def chat(name, String... args) {
		def id = main.ids[name]
		main.sauer('chat', "psay $id [${args.join(' ')}]")
		main.dumpCommands()
	}
	def tc_upmap(String... args) {
		main.sauer('tc_upmap', "tc_upmap ${args.join(' ')}")
		main.dumpCommands()
	}
	def requestmap() {
		main.requestMap()
	}
	def loadmap(name) {
		main.loadMap(name)
	}
	def tc_editent(String... args) {
		main.sauer('tc_editent', "tc_editent ${args.join(' ')}")
		main.dumpCommands()
	}
	def sendMap() {
		def cmd = new P2PMudCommand(main.peer.nodeId, "receiveMap")

		cmd.payload = mapsDoc
		main.peer.sendCmds(main.pastryCmd.from, cmd)
	}
	def receiveMap() {
		main.setMapsDoc(main.pastryCmd.payload)
	}
}
