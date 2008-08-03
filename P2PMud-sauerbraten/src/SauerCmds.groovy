public class SauerCmds {
	def main

	public SauerCmds(main) {
		this.main = main
	}
	def invoke(cmdString) {
		def args = cmdString.split() as List

		if (args.size() > 0) {
			if (args.size() == 1) {
				"${args[0]}"()
			} else {
				"${args[0]}"(*args[1..-1])
			}
		}
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
	def position(name, String... args) {
		if (main.names[name] == main.name) {
			main.swing.edt {
				for (def i = 0; i < args.size(); i += 2) {
					def field = main.fields[args[i]]
					
					if (field) field.text = args[i + 1]
				}
			}
			main.broadcast(["update ${main.name} ${args.join(' ')}"])
		}
	}
	def mapname(name) {
		println "Setting mapname: $name"
		if (name) {
			main.mapname = new File(name).getName()
		}
	}
	def requestmap() {
		main.anycast(['requestmap'])
	}
	def sendfile(map, id) {
		main.sendFile(map, id)
	}
}
