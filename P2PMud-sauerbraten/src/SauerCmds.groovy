public class SauerCmds {
	def main
	def args

	public SauerCmds(main) {
		this.main = main
	}
	def invoke(cmdString) {
		args = cmdString.split()
		def name = args[0]
		args = args.size() > 1 ? args[1..-1] : []
//		println "EXECUTING: $args"
		invokeMethod(name, null)
	}
	def login() {
		main.broadcast([
			"login $name ${args.join(' ')}"
		])
	}
	def showplayergui() {
		main.player.showGui(main)
	}
	def player() {
		def prop = args[0]

		main.player."$prop" = args[1]
		def val = main.player."$prop"
		println "set player $prop to $val"
	}
	def chat() {
		main.broadcast([
			"chat ${args.join(' ')}"
		])
	}
	def tc_upmap() {
		main.broadcast([
			"tc_upmap ${args.join(' ')}"
		])
	}
	def tc_editent() {
		main.broadcast([
			"tc_editent ${args.join(' ')}"
		])
	}
	def position() {
		if (main.names[args[0]] == main.name) {
			main.swing.edt {
				for (def i = 1; i < args.size(); i += 2) {
					def field = main.fields[args[i]]
					
					if (field) field.text = args[i + 1]
				}
			}
//			println "SENDING: update ${args[1..-1].join(' ')}"
			main.broadcast(["update ${main.name} ${args[1..-1].join(' ')}"])
		}
	}
	def mapname() {
		println "Setting mapname: ${args[0]}"
		if (args[0]) {
			main.mapname = new File(args[0]).getName()
		}
	}
	def requestmap() {
		main.anycast(['requestmap'])
	}
	def sendfile() {
		main.sendFile(args[0], args[1])
	}
}