package p2pmud;

public class Player {
	def name = "Zot"
	def team = "CTHULHU"
	def hitPoints = 100
	def damage = 0
	
	static def guiCreated

	def showGui(main) {
		String cmd = ""

		if (!guiCreated) {
			guiCreated = true
			cmd += """newgui playergui [
	guitext [Hit Points: ]
	guifield player_hit_points 25 [remotesend player hitPoints \$player_hit_points]
]
"""
		}
		main.sauer('playergui', main.cvtNewlines(cmd + """alias player_hit_points $hitPoints
showgui playergui"""))
		main.dumpCommands()
	}
}