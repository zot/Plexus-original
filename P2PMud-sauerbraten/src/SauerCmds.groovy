public class SauerCmds {
	def main
	def args

	public SauerCmds(main) {
		this.main = main
	}
	def invoke(cmdString) {
		args = cmdString.split()
		def name = args[0]
		args = args[1..-1]
		println "EXECUTING: $args"
		invokeMethod(name, null)
	}
	def login() {
		main.pastry([
			"login $name ${args.join(' ')}"
		])
	}
	def chat() {
		main.pastry([
			"chat ${args.join(' ')}"
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
			println "SENDING: update ${args[1..-1].join(' ')}"
			main.pastry(["update ${main.name} ${args[1..-1].join(' ')}"])
		}
	}
}