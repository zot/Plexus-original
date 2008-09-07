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
		def id = main.ids[main.pastryCmd.from.toStringFull()]

		if (!id) {
			id = "p$main.id_index"
			main.newPlayer(name, id)
		}
		main.sauer("${id}.update", "tc_setinfo $id " + args.join(' '))
		main.dumpCommands()
		main.playerUpdate(id, args)
	}
	def chat(name, String... args) {
		def id = main.ids[main.pastryCmd.from.toStringFull()]
		def player = main.getPlayer(main.pastryCmd.from.toStringFull())

		main.sauer('chat', "psay $player.name [${args.join(' ')}]")
		main.dumpCommands()
	}
	def tc_taunt() {
		def id = main.ids[main.pastryCmd.from.toStringFull()]
		def player = main.getPlayer(main.pastryCmd.from.toStringFull())

		main.sauer('taunt', "tc_taunt $player.name")
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
}
