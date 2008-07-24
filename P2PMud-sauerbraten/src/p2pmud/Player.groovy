package p2pmud;

public class Player {
	def name = "Zot"
	def team = "CTHULHU"
	def hitPoints = 100
	def damage = 0
	
	static def guiCreated

	def playerEditor(name, id) {
		return """
guitab $name
guitext [X: @(ent.x $id)]
guitext [Y: @(ent.y $id)]
guitext [Z: @(ent.z $id)]
guitext [R: @(ent.rol $id)]
guitext [P: @(ent.pit $id)]
guitext [Y: @(ent.yaw $id)]
guitext [E: @(ent.e $id)]
guitext [S: @(ent.m $id)]
guitext [M: @(ent.s $id)]
""";
	}
	def showGui(main) {
		def cmd = """
alias player_hit_points $hitPoints
newgui playergui [
	guititle Stats
	guitext [Hit Points: ]
	guifield player_hit_points 25 [remotesend player hitPoints \$player_hit_points]
	${playerEditor(name, 'p0')}
]
showgui playergui"""

		for (n in main.ids) {
			cmd += playerEditor(n.key, n.value)
		}
		main.sauer('playergui', main.cvtNewlines(cmd))
		main.dumpCommands()
	}
}
