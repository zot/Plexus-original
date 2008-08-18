public class Cmds {
	def main

	public Cmds(main) {
		this.main = main
	}
	def invoke(cmdString) {
		def args = cmdString.split() as List

		try {
			if (args.size() == 1) {
				"${args[0]}"()
			} else if (args.size() > 1) {
				"${args[0]}"(*args[1..-1])
			}
		} catch (Exception ex) {
			main.err("Problem occured while executing command: " + cmdString, ex)
		}
	}
}