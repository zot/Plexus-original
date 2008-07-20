import p2pmud.P2PMudPeer
import p2pmud.P2PMudCommandHandler
import java.awt.Dimension
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.border.LineBorder
import javax.swing.SpringLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints
import javax.swing.BoxLayout
import java.awt.BorderLayout
import static java.awt.BorderLayout.*
import static java.awt.GridBagConstraints.*
import java.awt.*
import javax.swing.*
import javax.swing.border.*
import groovy.swing.SwingBuilder
import net.miginfocom.swing.MigLayout

public class Test {
	def output = null
	def name
	def ids = [:]
	def names
	def count = 0
	def pendingCommands = [:]
	def sauerCmds = new SauerCmds(this)
	def pastryCmds = [:]
	def queueRunTime = Long.MAX_VALUE
	def queueBatchPeriod = 200
	def swing
	def xField
	def yField
	def zField
	def cmdField
	def runs = 0

	public static void main(String[] a) {
		if (a.length < 2) {
			println "Usage: Test port name pastryArgs"
			System.exit(1);
		}
		new Test()._main(a)
	}

	def _main(args) {
		name = args[1]
		names = [p0: name]
		ids[name] = 'p0'
		pastryCmds.login = {l ->
			sauer('echo', "echo ${l[0]} has joined!")
			pastryCmds.update(l)
		}
		pastryCmds.update = {u ->
			id = "p${u[0]}"
			if (!ids[u[0]]) {
				ids[u[0]] = id
				names[id] = u[0]
				sauer('prep', "createplayer $id")
			}
			sauer("$id.x", "ent.x $id ${u[1]}")
			sauer("$id.y", "ent.y $id ${u[2]}")
			sauer("$id.z", "ent.z $id ${u[3]}")
			sauer("$id.roll", "ent.roll $id ${u[4]}")
			sauer("$id.pitch", "ent.pitch $id ${u[5]}")
			sauer("$id.yaw", "ent.yaw $id ${u[6]}")
			dumpCommands()
		}
		pastryCmds.sauer = {s ->
			for (i = 0; i < s.size(); i++) {
				sauer(i, s.join(" "))
			}
			dumpCommands()
		}
		pastryCmds.chat = {c ->
			sauer('chat', "psay m0 [${c.join(' ')}]")
			dumpCommands()
		}
		swing = new SwingBuilder()
		swing.build {
			f = frame(title: 'Position', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				label(name: 'xLabel', text: "x: ")
				xField = textField(name: 'xField', actionPerformed: {newX()}, focusLost: {newX()}, constraints: 'wrap, growx')
				label(name: 'yLabel', text: "y: ")
				yField = textField(name: 'yField', actionPerformed: {newY()}, focusLost: {newY()}, constraints: 'wrap, growx')
				label(name: 'zLabel', text: "z: ")
				zField = textField(name: 'zField', actionPerformed: {newZ()}, focusLost: {newZ()}, constraints: 'wrap, growx')
				label(name: 'cmdLabel', text: "Command: ")
				cmdField = textField(name: 'cmdField', actionPerformed: {cmd()}, focusLost: {cmd()}, constraints: 'wrap, growx')
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
		start(args[0])
		P2PMudPeer.main({cmd -> cmd.msgs.each {runCommand(it, pastryCmds)}} as P2PMudCommandHandler, args[2..-1] as String[])
	}
	def start(port) {
		Thread.startDaemon {
			def sock = new ServerSocket(Integer.parseInt(port))

			println "READY"
			while (true) {
				Socket client = sock.accept({
					println("Got connection...")
					output = it.getOutputStream()
					init()
					it.getInputStream().eachLine {
						sauerCmds.invoke(it)
					}
					try {it.shutdownInput()} catch (Exception ex) {}
					try {it.shutdownOutput()} catch (Exception ex) {}
					println "Disconnect"
				});
			}
		}
	}
	def sauer(key, value) {
		synchronized (pendingCommands) {
			pendingCommands[key] = value
		}
	}
	def runCommand(str, cmds) {
		def a = str.split()
		def func = cmds[a[0]]
	
		println "EXECUTING: $a"
		if (func) {
			func(a.length > 1 ? a[1..-1] : [])
		}
	}
	def dumpCommands() {
		if (output) {
			synchronized (pendingCommands) {
				if (!pendingCommands.isEmpty()) {
					output << pendingCommands.collect{it.value}.join(";") + '\n'
					println "SENT: ${pendingCommands.collect{it.value}.join(';') + '\n'}"
					pendingCommands = [:]
				}
			}
			output.flush()
		}
	}
	def sauerEnt(label, field) {
		if (field.text && field.text[0]) {
			def cmd = "ent.$label p0 ${field.text}"
			println "NEW $label: $cmd"
			sauer(label, cmd)
			dumpCommands()
		}
	}
	def newX() {
		sauerEnt('x', xField)
	}
	def newY() {
		sauerEnt('y', yField)
	}
	def newZ() {
		sauerEnt('z', zField)
	}
	def cmd() {
		if (cmdField.text && cmdField.text[0]) {
			sauer('cmd', cmdField.text)
			cmdField.text = ""
			dumpCommands()
		}
	}
	def init() {
	 	sauer('init', [
			"alias p2pname [$name]",
			'alias prep [if (= $arg2 0) edittoggle; mfreeze $arg1; if (= $arg2 0) edittoggle]',
			'alias chat [echo $p2pname says: $arg1;remotesend chat $arg1]',
			'bind RETURN [saycommand [/chat ""]]',
			'editbind RETURN [saycommand [/chat ""]]',
			'alias emote [echo $p2pname $arg1;remotesend chat $arg1]',
			'bind SEMICOLON [saycommand [/emote ""]]',
			'editbind SEMICOLON [saycommand [/emote ""]]',
			'echo INIT'
		].join(';'))
	 	dumpCommands()
	}
	
	def pastry(cmds) {
		P2PMudPeer.test.sendCmds(cmds as String[])
	}
}
