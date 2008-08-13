public class Cmds {
	def main

	public Cmds(main) {
		this.main = main
	}
	def invoke(cmdString) {
		def args = cmdString.split() as List

		if (args.size() == 1) {
			"${args[0]}"()
		} else if (args.size() > 1) {
			"${args[0]}"(*args[1..-1])
		}
	}
}