import p2pmud.P2PMudCommand
public class PastryCmds extends Cmds {
	public PastryCmds(main) {
		super(main)
	}
	def login(name, String... args) {
		main.sauer('echo', "echo ${name} has joined!")
		update(name, *args)
	}
	def update(name, String... args) {
		playerUpdate(main.pastryCmd.from.toStringFull(), name, args)
	}
	def playerUpdate(pid, name, args) {
		def id = main.ids[pid]
		def updateStr = "tc_setinfo $id ${args.join(' ')}"

		if (!id ) {		
			id = main.newPlayer(name, pid)
		}
		main.sauer("${id}.update", updateStr)
		main.dumpCommands()
		main.playerUpdate(pid, name, args)
		id
	}
	def chat(name, String... args) {
		def peer = main.pastryCmd.from.toStringFull()
		def id = main.ids[peer]
		//def player = main.getPlayer(peer)

		main.sauer('chat', "psay $id [${args.join(' ')}]")
		main.dumpCommands()
	}
	def tc_taunt() {
		def id = main.ids[main.pastryCmd.from.toStringFull()]

		main.sauer('taunt', "tc_taunt $id")
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
	def sendCloudProperties() {
		def cmd = new P2PMudCommand(main.peer.nodeId, "receiveCloudProperties")

		cmd.payload = main.cloudProperties.transmitProperties()
		main.peer.sendCmds(main.pastryCmd.from, cmd)
	}
	def receiveCloudProperties() {
		main.receiveCloudProperties(main.pastryCmd.payload)
	}
	def removeCloudProperty(key) {
		main.removeCloudProperty(key)
	}
	def setCloudProperty(key, String... values) {
		main.setCloudProperty(key, values.join(' '))
	}
	def whisperPlayer(from, String... msg) {
		main.sauer('whisp', "echo Player $from whispers: ${msg.join(' ')}")
		main.dumpCommands();
	}
	def addCostume(dir, name, thumb) {
		main.addCostume(dir, name, thumb)
	}
	def sendCostumes() {
	}
	def sendPlayerLocations() {
		def cmd = new P2PMudCommand(main.peer.nodeId, "receivePlayerLocations")
		def locs = main.cachedPlayerLocations.clone()

		locs[main.peerId] = [main.name, main.myCachedLocation]
		cmd.payload = locs
		main.peer.sendCmds(main.pastryCmd.from, cmd)
	}
	def receivePlayerLocations() {
		println "RECEIVED PLAYER LOCATIONS: $main.pastryCmd.payload"
		main.pastryCmd.payload.each {
			if (it.key != main.peerId && !main.cachedPlayerLocations[it.key]) {
				def id = playerUpdate(it.key, it.value[0], it.value[1])
				println "PLACING PLAYER: $it.key ($id, ${it.value[0]}) at ${it.value[1]}"
			}
		}
	}
}
