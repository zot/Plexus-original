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
	def id_index = 1
	def ids = [:]
	def names
	def count = 0
	def pendingCommands = [:]
	def sauerCmds = new SauerCmds(this)
	def pastryCmds = [:]
	def queueRunTime = Long.MAX_VALUE
	def queueBatchPeriod = 200
	def swing
	def fields = [:]
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
			def name =u[0] 
			def id = ids[name]
			if (!id) {
				id = "p$id_index"
				ids[name] = id
				++id_index
				names[id] = name
				sauer('prep', "createplayer $id")
			}
			for (def ix = 1; ix < u.size(); ix += 2)
			{
				def f = u[ix]
				def v = u[ix+1]
				sauer("${id}.$f", "ent.$f $id $v")
			}
			dumpCommands()
		}
		
		pastryCmds.sauer = {s ->
			for (def i = 0; i < s.size(); i++) {
				sauer(i, s.join(" "))
			}
			dumpCommands()
		}
		pastryCmds.chat = {c ->
			def name = c[0] 
			def id = ids[name]
			sauer('chat', "psay $id [${c.join(' ')}]")
			dumpCommands()
		}
//		pastryCmds.chat = {c ->
//			def name = u[0] 
//			def id = ids[name]
//
//			sauer('chat', "psay $id [${c.join(' ')}]")
//			dumpCommands()
//		}
		swing = new SwingBuilder()
		swing.build {
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {sauerEnt(key)}, focusLost: {sauerEnt(key)}, constraints: 'wrap, growx')
			}
			def f = frame(title: 'Position', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				field('x: ', 'x')
				field('y: ', 'y')
				field('z: ', 'z')
				field('vx: ', 'vx')
				field('vy: ', 'vy')
				field('vz: ', 'vz')
				field('fx: ', 'fx')
				field('fy: ', 'fy')
				field('fz: ', 'fz')
				field('roll: ', 'rol')
				field('pitch: ', 'pit')
				field('yaw: ', 'yaw')
				field('in water: ', 'iw')
				field('time in air: ', 'tia')
				field('strafe: ', 's')
				field('move: ', 'm')
				field('physics state: ', 'ps')
				label(text: "Command: ")
				fields.cmd = textField(actionPerformed: {cmd()}, focusLost: {cmd()}, constraints: 'wrap, growx')
				button(text: "update", actionPerformed: {pastryCmds.update(["floopy", "1597.093994", "1620.530884", "2062.024658", "0.000000", "-55.000015", "348.454498"])})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
		//button(text: "update", actionPerformed: {pastryCmds.update(["floopy", "1597.093994", "1620.530884", "2062.024658", "0.000000", "-55.000015", "348.454498"])})
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
	def sauerEnt(label) {
		if (fields[label]?.text && fields[label].text[0]) {
			def cmd = "ent.$label ${ids[name]} ${fields[label].text}"
			println "NEW $label: $cmd"
			sauer(label, cmd)
			dumpCommands()
		}
	}
	def cmd() {
		if (fields.cmd.text && fields.cmd.text[0]) {
			sauer('cmd', fields.cmd.text)
			fields.cmd.text = ""
			dumpCommands()
		}
	}
	def init() {
	 	sauer('init', [
			"alias p2pname [$name]",
			'alias prep [if (= $arg2 0) edittoggle; mfreeze $arg1; if (= $arg2 0) edittoggle]',
			'alias chat [echo $p2pname says: $arg1;remotesend chat $p2pname $arg1]',
			'bind RETURN [saycommand [/chat ""]]',
			'editbind RETURN [saycommand [/chat ""]]',
			'alias emote [echo $p2pname $arg1;remotesend chat $p2pname $arg1]',
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