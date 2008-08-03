public class Plexus {
	def static guid
	def static props
//	def static runCount

	public static void main(String[] args) {
		if (!args.length) {
			Prep.main()
			args = Prep.mainArgs
			props = Prep.props
			guid = props.guid
//			runCount = Integer.parseInt(props.runCount)
			println "guid: $guid"
		}
		Test.main(*args)
	}
}
