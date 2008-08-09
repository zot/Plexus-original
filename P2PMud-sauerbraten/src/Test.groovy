import java.text.SimpleDateFormat
import rice.p2p.commonapi.IdFactory
import rice.Continuation
import rice.pastry.Id
import p2pmud.P2PMudFile
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
	def pastryCmds = new PastryCmds(this)
	def queueRunTime = Long.MAX_VALUE
	def queueBatchPeriod = 200
	def swing
	def fields = [:]
	def runs = 0
	def pastryCmd
	def player = new Player()
	def sauerDir
	def mapPrefix = 'packages/dist/storage'
	def peer
	def mapname
	def mapsDoc
	def mapTopic
	def plexusTopic

	def static sauerExec
	def static soleInstance
	def static TIME_STAMP = new SimpleDateFormat("yyyyMMdd-HHmmsszzz")
	def static final PLEXUS_KEY = "Plexus: main"
	def static final WORLDS_KEY = "Plexus: worlds"
	def static final SPHINX_KEY = "Plexus: sphinx"

	public static void main(String[] a) {
		if (a.length < 2) {
			println "Usage: Test port name pastryArgs"
			System.exit(1);
		}
		new Test()._main(a)
	}
	def static verifySauerdir(dir) {
		if (!dir) return false
		def f = dir as File
		def subs = ['data', 'docs', 'packages/base']

		for (s in subs) {
			if (!new File(f, s).isDirectory()) {
				return false
			}
		}
		return true
	}
	def tst(a, b) {
		println "TST: $a, $b"
	}
	def _main(args) {
		soleInstance = this
		sauerDir = System.getProperty("sauerdir");
		name = args[1]
		if (!verifySauerdir(sauerDir)) {
			usage("sauerdir must be provided")
		} else if (!name) {
			usage("name must be provided")
		}
		sauerDir = new File(sauerDir)
		new File(sauerDir, mapPrefix).mkdirs()
		launchSauer();
		names = [p0: name]
		ids[name] = 'p0'
		swing = new SwingBuilder()
		swing.build {
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {sauerEnt(key)}, focusLost: {sauerEnt(key)}, constraints: 'wrap, growx')
			}
			def f = frame(title: 'Plexus', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
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
				field('max speed: ', 'ms')
				label(text: "Command: ")
				fields.cmd = textField(actionPerformed: {cmd()}, constraints: 'wrap, growx')
				button(text: "Launch 3D", actionPerformed: {launchSauer()})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
		start(args[0])
		P2PMudPeer.main({id, topic, cmd ->
				pastryCmd = cmd
				cmd.msgs.each {pastryCmds.invoke(it)}
			} as P2PMudCommandHandler,
			{
				//sauer('peers', "peers ${peer.getNeighborCount()}")
				//dumpCommands()
			},
			args[2..-1] as String[])
		peer = P2PMudPeer.test
		plexusTopic = peer.subscribe(peer.buildId(PLEXUS_KEY), null)
//		connectWorld(peer.buildId(SPHINX_KEY).toStringFull())
		if (peer.node.getLeafSet().getUniqueCount() == 1) {
			initBoot()
		} else {
			initJoin()
		}
//		mapTopic = peer.subscribe(peer.buildId("updates for bubba"))
//		mapsDocId = peer.buildId(mapsDocKey)
//		println "Node ID: ${peer.node.getId().toStringFull()}"
		if (!Plexus.props.nodeId) {
			Plexus.props.nodeId = peer.node.getId().toStringFull()
			Plexus.saveProps()
		}
	}
	// launch sauer in its own thread
	def launchSauer() {
		println ("Going to exec $sauerExec")
		Thread.start {
			if (sauerExec) {
				Runtime.getRuntime().exec(sauerExec)
			}
		};
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
						try {sauerCmds.invoke(it)} catch (Exception ex) {}
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
	
		if (func) {
			func(a.length > 1 ? a[1..-1] : [])
		}
	}
	def dumpCommands() {
		if (output) {
			synchronized (pendingCommands) {
				if (!pendingCommands.isEmpty()) {
					output << pendingCommands.collect{it.value}.join(";") + '\n'
					pendingCommands = [:]
				}
				output.flush()
			}
		}
	}
	def sauerEnt(label) {
		if (fields[label]?.text && fields[label].text[0]) {
			def cmd = "ent.$label ${ids[name]} ${fields[label].text}"
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
			"remotesend mapname (mapname)",
			"cleargui 1",
			"showgui Plexus",
			'echo INIT'
		].join(';'))
	 	dumpCommands()
	}
	def broadcast(cmds) {
		if (peer) peer.broadcastCmds(mapTopic, cmds as String[])
	}
	def anycast(cmds) {
		if (peer) peer.anycastCmds(mapTopic, cmds as String[])
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
		"$name-${TIME_STAMP.format(new Date())}"
	}
	def sendFile(map, id) {
//		println "Saved map, storing in PAST, branch: packages/p2pmud/${mapname}.ogz, path: packages/p2pmud/${map}.ogz"
//		peer.wimpyStoreFile(new File(sauerDir, "packages/plexus/cache"), "packages/plexus/${mapname}.ogz", [
//			receiveResult: {file ->
//				if (file) {
//					println "Sending load cmd for file: $file: loadmap ${file.getId().toStringFull()}"
//					peer.sendCmds(Id.build(id), ["loadmap ${file.getId().toStringFull()}"] as String[])
//				} else {
//					println "Could not store file for $mapname"
//				}
//			},
//			receiveException: {exception -> err("Error storing file: $mapname", exception)}
//		] as Continuation, false);
	}
	def loadMap(id) {
//		println "Received load cmd for map: ${id}"
//		peer.wimpyGetFile(Id.build(id), sauerDir, [
//			receiveResult: {result ->
//				def file = result[0]
//				def p2pFile = result[1]
//				def missing = result[2]
//
//				if (missing.isEmpty()) {
//					def mapPath = "p2pmud/${new File(p2pFile.path).getName()}"
//
//					println "Retrieved map from PAST: $file, executing: map [$mapPath]"
//					sauer('load', "echo loading new map: [$mapPath]; map [$mapPath]")
//					dumpCommands()
//				} else {
//					println "Couldn't load file: $file"
//				}
//			},
//			receiveException: {exception -> err("Error retrieving file: $id", exception)}
//		] as Continuation)
	}
	def err(msg, err) {
		System.err.println(msg)
		err.printStackTrace();
	}
	def initBoot() {
		setMapsDoc([
			Sphinx: peer.buildId(SPHINX_KEY).toStringFull()
		])
	}
	def setMapsDoc(doc) {
		def mapsGui = "newgui Worlds ["

		for (world in doc) {
			mapsGui += "guibutton [$world.key] [connectWorld $world.value]\n"
		}
		mapsGui += "]"
		mapsDoc = doc
		sauer('maps', cvtNewlines(mapsGui))
	}
	def initJoin() {
		peer.anycastCmds(plexusTopic, "sendMaps")
	}
	def connectWorld(id) {
		println "CONNECTING TO WORLD: $id"
		if (mapTopic) {
			peer.unsubscribe(mapTopic)
		}
		mapTopic = peer.subscribe(Id.build(id), [
			receiveResult: {topic ->
				mapTopic = topic
				println "SUBSCRIBED TO $topic"
				//peer.anycastCmds(mapTopic, "requestmap")
			},
			receiveException: {exception -> err("Couldn't subscribe to topic: ", id)}
		] as Continuation)
	}
}
