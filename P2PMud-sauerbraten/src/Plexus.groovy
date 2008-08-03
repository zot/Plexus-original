public class Plexus {
	def static props
	def static runCount

	public static void main(String[] args) {
		if (!args.length) {
			Prep.main()
			args = Prep.mainArgs
			props = Prep.props
			runCount = props.runCount ? Integer.parseInt(props.runCount) + 1 : 0;
			props.runCount = String.valueOf(runCount)
			saveProps()
		}
		Test.main(*args)
	}
	public static saveProps() {
		Prep.saveProps()
	}
}
