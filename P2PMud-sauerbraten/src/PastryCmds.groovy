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
			main.peerToSauerIdMap[main.pastryCmd.from.toStringFull()] = id
			println main.peerToSauerIdMap
			main.sauer('prep', "echo [Welcome player $name to this world.]; createplayer $id $name")
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
	def loadmap(name) {
		main.loadMap(name)
	}
	def tc_editent(String... args) {
		main.sauer('tc_editent', "tc_editent ${args.join(' ')}")
		main.dumpCommands()
	}
	def sendMaps() {
		def cmd = new P2PMudCommand(main.peer.nodeId, "receiveMaps")

		cmd.payload = main.idToMap
		main.peer.sendCmds(main.pastryCmd.from, cmd)
	}
	def receiveMaps() {
		main.setMapsDoc(main.pastryCmd.payload, true)
	}
	def receivePlayers() {
		main.setPlayersDoc(main.pastryCmd.payload)
		main.updateMyPlayerInfo()
	}
	def addMap(topic, tree, String... name) {
		main.addMap(topic, tree, name.join(' '))
	}
	def updatePlayer(node, String... info) {
		main.updatePlayer(node, info)
	}
	def removePlayer(node) {
		main.removePlayer(node)
	}
	def sendPlayers() {
		def cmd = new P2PMudCommand(main.peer.nodeId, "receivePlayers")

		cmd.payload = main.playersDoc
		main.peer.sendCmds(main.pastryCmd.from, cmd)
	}
	def whisperPlayer(from, msg) {
		main.sauer('whisp', "echo Player $from whispers: $msg")
		main.dumpCommands();
	}
}
