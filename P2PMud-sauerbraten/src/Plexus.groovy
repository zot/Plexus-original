public class Plexus {
	def static guid
	def static props = [:] as Properties

	public static void main(String[] args) {
		if (!args.length) {
			Prep.main()
			args = Prep.mainArgs
			println "guid: $guid"
		}
		Test.main(*args)
	}
}
