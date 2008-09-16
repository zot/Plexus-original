public class SauerCmds extends Cmds {
	def currentPosition
	def positionLock = new Object()

	public SauerCmds(main) {
		super(main)
	}
	def login() {
		main.broadcast([
			"login $name ${args.join(' ')}"
		])
	}
	def showplayergui() {
		main.playerObj.showGui(main)
	}
	def player(prop, value) {
		main.playerObj."$prop" = value
		def val = main.playerObj."$prop"
		println "set player $prop to $val"
	}
	def chat(String... args) {
		main.broadcast([
			"chat ${args.join(' ')}"
		])
	}
	def tc_upmap(String... args) {
		main.broadcast([
			"tc_upmap ${args.join(' ')}"
		])
	}
	def tc_taunt() {
		main.broadcast([
			"tc_taunt"
		])
	}
	def tc_editent(String... args) {
		main.broadcast([
			"tc_editent ${args.join(' ')}"
		])
	}
	def tc_newmap(String name) {
		println "newmap: $name"
		main.sauer("delp", "deleteallplayers")
		main.dumpCommands()
		main.mapname = name
		main.updateMyPlayerInfo()
	}
	def levelTrigger(trigger) {
		main.levelTrigger(trigger)
	}
	def position(name, String... args) {
		if (main.names && main.names[name] == main.peerId) {
			if (main.swing) {
				synchronized (positionLock) {
					currentPosition = args
				}
				main.swing.doLater {
					def pos

					synchronized (positionLock) {
						pos = currentPosition
						currentPosition = null
					}
					if (pos) {
						for (def i = 0; i < args.size(); i += 2) {
							def field = main.fields[args[i]]
						
							if (field) field.text = args[i + 1]
						}
					}
				}
			}
			main.broadcast(["update ${main.name} ${args.join(' ')}"])
		}
	}
	def connectWorld(id) {
		main.connectWorld(id)
	}
	def pushMap(privateMap, String... nameArgs) {
		main.pushMap(privateMap, *nameArgs)
	}
	def pushMap(mapName, update) {
		println "Sauer sent: pushMap $mapName"
		main.pushMap(mapName, "true".equalsIgnoreCase(update))
	}
	def createWorld(mapName) {
		println "Create World: $mapName"
	}
	def copyWorld(mapName) {
		println "Copy World: $mapName"
		main.copyWorld(mapName)
	}
	def whisperPlayer(from, to, String... msg) {
		main.send(to, ["whisperPlayer $from ${msg.join(' ')}"])
	}
	def shareMap(to) {
		main.shareMap(to)
	}
	def activePortals(String... portalIds) {
		main.activePortals(portalIds)
	}
	def bindPortals(String... bindings) {
		for (def i = 0; i < bindings.length; i += 2) {
			main.bindPortal(bindings[i], bindings[i + 1])
		}
	}
	def firePortal(id) {
		println "firePortal $id"
		main.firePortal(id)
	}
	def createPortal(name, id) {
		main.createPortal(name, id)
	}
	def pushCostume(name) {
		println "PUSHING COSTUME FROM SAUER: $name"
		main.pushCostume(name)
	}
	def useCostume(name, id) {
		main.useCostume(name, id)
	}
	def hit(shooter, target, type) {
		println "HIT shooter: $shooter, target: $target, type: $type"
	}
}
