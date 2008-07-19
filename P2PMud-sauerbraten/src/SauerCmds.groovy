public class SauerCmds {
	def main;

	def SauerCmds(main) {
		this.main = main
	}
	def position(ent, x, y, z, String... orientation) {
		main.swing.edt {
			xField.text = x
			yField.text = y
			zField.text = z
		}
		println "SENDING: update $ent $x $y $z $roll}"
//		main.pastryCmds.invokeMethod('update', [name, x, y, z] + orientation)
	}
	def login(String... args) {
//		main.pastryCmds.invokeMethod('login', args)
	}
}