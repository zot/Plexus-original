import java.text.SimpleDateFormat
import rice.p2p.commonapi.IdFactory
import rice.Continuation
import rice.pastry.Id
import p2pmud.P2PMudFile
import p2pmud.P2PMudFilePath
import p2pmud.P2PMudPeer
import p2pmud.P2PMudCommandHandler
import p2pmud.Player
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
	def pastryCmd
	def player = new Player()
	def sauerDir
	def maps
	def peer
	def mapname
	
	def static TIME_STAMP = new SimpleDateFormat("yyyyMMdd-HHmmsszzz")

	public static void main(String[] a) {
		if (a.length < 2) {
			println "Usage: Test port name pastryArgs"
			System.exit(1);
		}
		new Test()._main(a)
	}

	def _main(args) {
		sauerDir = System.getProperty("sauerdir");
		name = args[1]
		if (!verifySauerdir(sauerDir)) {
			usage("sauerdir must be provided")
		} else if (!name) {
			usage("name must be provided")
		}
		sauerDir = new File(sauerDir)
		maps = new File(sauerDir, 'packages/p2pmud')
		maps.mkdirs()
		names = [p0: name]
		ids[name] = 'p0'
		pastryCmds.login = {l ->
			sauer('echo', "echo ${l[0]} has joined!")
			pastryCmds.update(l)
		}
		pastryCmds.update = {u ->
			def name = u[0] 
			def id = ids[name]
			if (!id) {
				id = "p$id_index"
				ids[name] = id
				++id_index
				names[id] = name
				sauer('prep', "createplayer $id $name")
			}
			sauer("${id}.update", "tc_setinfo $id " + u[1..-1].join(' '))
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
		pastryCmds.tc_upmap = {c ->
			sauer('tc_upmap', "tc_upmap ${c.join(' ')}")
			dumpCommands()
		}
		pastryCmds.requestmap = {c ->
			requestMap()
		}
		pastryCmds.loadmap = {c ->
			loadMap(c[0])
		}
		pastryCmds.tc_editent = {c ->
			sauer('tc_editent', "tc_editent ${c.join(' ')}")
			dumpCommands()
		}
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
				field('strafe: ', 's')
				field('edit: ', 'e')
				field('move: ', 'm')
				field('physics state: ', 'ps')
				label(text: "Command: ")
				fields.cmd = textField(actionPerformed: {cmd()}, focusLost: {cmd()}, constraints: 'wrap, growx')
				button(text: "update", actionPerformed: {pastryCmds.update(["floopy", "x", "1597.093994", "y", "1620.530884", "z", "2062.024658", "rol", "0.000000", "pit", "-55.000015", "yaw", "348.454498"])})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
		//button(text: "update", actionPerformed: {pastryCmds.update(["floopy", "1597.093994", "1620.530884", "2062.024658", "0.000000", "-55.000015", "348.454498"])})
		start(args[0])
		P2PMudPeer.main({cmd ->
			pastryCmd = cmd
			cmd.msgs.each {runCommand(it, pastryCmds)}
		} as P2PMudCommandHandler, args[2..-1] as String[])
		peer = P2PMudPeer.test
	}
	def verifySauerdir(dir) {
		if (!dir) return false
		def f = new File(dir)
		def subs = ['data', 'docs', 'packages/base']

		for (s in subs) {
			if (!new File(f, s).isDirectory()) {
				return false
			}
		}
		return true
	}
	def usage(msg) {
		println msg
		println "usage: ${getClass().name} port name"
		println "System property sauerdir must hold your Sauerbraten distribution directory"
	}
	def start(port) {
		Thread.startDaemon {
			def sock = new ServerSocket(Integer.parseInt(port))

			println "READY"
			while (true) {
				Socket client = sock.accept({
					println("Got connection from sauerbraten...")
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
	
//		println "EXECUTING: $a"
		if (func) {
			func(a.length > 1 ? a[1..-1] : [])
		}
	}
	def dumpCommands() {
		if (output) {
			synchronized (pendingCommands) {
				if (!pendingCommands.isEmpty()) {
					output << pendingCommands.collect{it.value}.join(";") + '\n'
//					println "SENT: ${pendingCommands.collect{it.value}.join(';') + '\n'}"
					pendingCommands = [:]
				}
			}
			output.flush()
		}
	}
	def sauerEnt(label) {
		if (fields[label]?.text && fields[label].text[0]) {
			def cmd = "ent.$label ${ids[name]} ${fields[label].text}"
//			println "NEW $label: $cmd"
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
			"remotesend mapname (mapname)",
			'echo INIT'
		].join(';'))
	 	dumpCommands()
	}
	def broadcast(cmds) {
		peer.broadcastCmds(cmds as String[])
	}
	def anycast(cmds) {
		peer.anycastCmds(cmds as String[])
	}
	def cvtNewlines(str) {
		println "${str.replaceAll(/\n/, ';')}"
		return str.replaceAll(/\n/, ';')
	}
	def requestMap() {
		def uname = uniqify(mapname)

		println "Map requested.  Sending: $mapname ($uname)"
		sauer('save', "savemap p2pmud/$uname;remotesend sendfile $uname ${pastryCmd.from.toStringFull()}")
		dumpCommands()
	}
	def uniqify(name) {
		"name-${TIME_STAMP.format(new Date())}"
	}
	def sendFile(map, id) {
		println "Saved map, storing in PAST"
		peer.wimpyStoreFile(mapname, maps, "${map}.ogz", [
			receiveResult: {file ->
				if (file) {
					println "Sending load cmd for file: $file"
					peer.sendCmds(Id.build(id), ["loadmap ${file.getId().toStringFull()}"] as String[])
				} else {
					println "Could not store file for $mapname"
				}
			},
			receiveException: {exception -> err("Error storing file: $mapname", exception)}
		] as Continuation);
	}
	def loadMap(id) {
		println "Received load cmd for map: ${id}"
		peer.wimpyGetFile(Id.build(id), maps, [
			receiveResult: {result ->
				println "Retrieved map from PAST: $result, loading..."
				def output = new FileOutputStream(new File(maps, "${result.branch}.ogz"))
				output.write(result.data)
				output.close()
				sauer('load', "echo loading new map: ${result.branch}; loadmap ${new File(maps, result.path)}")
				dumpCommands()
			},
			receiveException: {exception -> err("Error retrieving file: $id", exception)}
		] as Continuation)
	}
	def err(msg, err) {
		System.err.println(msg)
		err.printStackTrace();
	}
}
