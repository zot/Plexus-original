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
		main.player.showGui(main)
	}
	def player(prop, value) {
		main.player."$prop" = value
		def val = main.player."$prop"
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
	def tc_editent(String... args) {
		main.broadcast([
			"tc_editent ${args.join(' ')}"
		])
	}
	def tc_newmap(String name) {
		println "newmap: $name"
		main.mapname = name
		main.updateMyPlayerInfo()
	}
	def position(name, String... args) {
		if (main.names[name] == main.name) {
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
	def pushMap(name) {
		main.pushMap(name)
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
}
