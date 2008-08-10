import p2pmud.Tools
import java.text.SimpleDateFormat
import rice.p2p.commonapi.IdFactory
import rice.Continuation
import rice.pastry.Id
import p2pmud.P2PMudFile
import p2pmud.P2PMudPeer
import p2pmud.P2PMudCommandHandler
import p2pmud.Player
import p2pmud.Dungeon
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
	def plexusDir
	def cacheDir
	def mapPrefix = 'packages/dist/storage'
	def peer
	def mapname
	def mapsDoc
	def mapTopic
	def plexusTopic
	def mapsLock = new Object()

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
		if (Plexus.props.headless == '0') {
			sauerDir = System.getProperty("sauerdir");
			name = args[1]
			if (!verifySauerdir(sauerDir)) {
				usage("sauerdir must be provided")
			} else if (!name) {
				usage("name must be provided")
			}
			sauerDir = new File(sauerDir)
			plexusDir = new File(sauerDir, "packages/plexus")
			cacheDir = new File(plexusDir, "cache")
			def pastStor = new File(plexusDir, "PAST-storage")
			Tools.deleteAll(pastStor)
			pastStor.mkdirs()
			System.setProperty('past.storage', pastStor.getAbsolutePath())
			new File(sauerDir, mapPrefix).mkdirs()
			if (Plexus.props.auto_sauer != '0') launchSauer();
		}
		names = [p0: name]
		ids[name] = 'p0'
		if (Plexus.props.headless == '0') {
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
					button(text: "Generate Dungeon", actionPerformed: {generateDungeon()})
				}
				f.size = [500, (int)f.size.height] as Dimension
			}
			start(args[0])
		}
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
	// generate a random dungeon
	def generateDungeon() {
		println ("Going to generate dungeon")
		Thread.start {
			sauer('newmap', 'newmap')
			sauer('music', 'musicvol 0')
			dumpCommands()
			def dungeon = new Dungeon(6, 6)
			
		    dungeon.generate_maze();
			
			def blocks = dungeon.convertTo3DBlocks()
			
			for (def i = 0; i < dungeon.blockRows; ++i) {
				for (def j = 0; j < dungeon.blockCols; ++j) {
					if (blocks[i][j] != 'X') {
						def x = i * 32
						def y = j * 32
						//println "x: $x y: $y"
						sauer('delcube', "selcube $x $y 480 1 1 1 32 5; delcube")
						dumpCommands()
						
						if (blocks[i][j] == 'e') {
							
						} else if (blocks[i][j] == 's') {
							
						}
					}
				}
			}
			
			for (def side = 0; side < 6; ++side)
				sauer("texture$side", "selcube 0 0 480 2 2 2 512 $side; edittex -999; edittex 261")
			dumpCommands()
			
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
				Socket client = sock.accept {
					println("Got connection from sauerbraten...")
					output = it.getOutputStream()
					init()
					it.getInputStream().eachLine {
						try {
							sauerCmds.invoke(it)
						} catch (Exception ex) {
							ex.printStackTrace()
						}
					}
					try {it.shutdownInput()} catch (Exception ex) {}
					try {it.shutdownOutput()} catch (Exception ex) {}
					println "Disconnect"
				};
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
	def loadMap(name, id) {
		println "Loading map: ${id}"
		if (id instanceof String) {
			id = Id.build(id)
		}
		peer.wimpyGetFile(id, cacheDir, [
			receiveResult: {result ->
				def filename = result[0]
				def missing = result[1]
				def file = result[2]

				if (!missing || missing.isEmpty()) {
					def mapDir = new File(plexusDir, "maps/$name")
					def count = 0
					def backup = mapDir
					
					while (backup.exists()) {
						count++
						backup = new File("${mapDir.getAbsolutePath()}-$count")
					}
					if (count && !mapDir.renameTo(backup)) {
						sauer('msg', "showmessage [Could not backup old map dir: $backup]")
						dumpCommands()
						return
					}
					mapDir.getParentFile().mkdirs()
					P2PMudFile.fetchDirFromProperties(cacheDir, id, Tools.properties(P2PMudFile.filename(cacheDir, id)), mapDir, [
						receiveResult: {
							def prefixLen = new File(sauerDir, "packages").getAbsolutePath().length() + 1
							def mapPath = mapDir.getAbsolutePath().substring(prefixLen)

							println "Retrieved map from PAST: $filename, executing: map [$mapPath/$name]"
							sauer('load', "echo loading new map: [$mapPath/$name]; map [$mapPath/$name]")
							dumpCommands()
						},
						receiveException: {err("Error retrieving file: $id", it)}
					], false)
				} else {
					println "Couldn't load file: $filename"
				}
			},
			receiveException: {err("Error retrieving file: $id", it)}
		] as Continuation)
	}
	def err(msg, err) {
		System.err.println(msg)
		err.printStackTrace();
	}
	def initBoot() {
		def maps = new File(plexusDir, "mapsdoc")
		
		if (maps.exists()) {
			setMapsDoc(Tools.properties(maps), false)
		} else {
			setMapsDoc([:] as Properties, true)
		}
	}
	def addMap(mapName, id) {
		synchronized (mapsLock) {
			mapsDoc[mapName] = id
		}
		setMapsDoc(mapsDoc, true)
	}
	def setMapsDoc(doc, save) {
		synchronized (mapsLock) {
			def mapsGui = "newgui Worlds ["

			for (world in doc) {
				mapsGui += "guibutton [$world.key] [remotesend connectWorld $world.key $world.value]\n"
			}
			mapsGui += "]"
			mapsDoc = doc
			sauer('maps', cvtNewlines(mapsGui))
			dumpCommands()
			if (save) {
				Tools.store(doc, new File(plexusDir, 'mapsdoc'), "Maps document")
			}
		}
	}
	def initJoin() {
		peer.anycastCmds(plexusTopic, "sendMaps")
	}
	def connectWorld(name, id) {
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
		loadMap(name, id)
	}
	def initiatePush(mapName) {
		def mapFile = new File(sauerDir, "packages/$mapName")
		mapFile.getParentFile().mkdirs()
		sauer('push', "savemap $mapName; remotesend pushMap $mapName")
		dumpCommands()
	}
	def pushMap(mapName) {
		println "PUSH: $mapName"
		def mapFile = new File(sauerDir, "packages/$mapName")
		def parent = mapFile.getParentFile()
		def mapCount = 0

		mapFile.getParentFile().eachFileMatch(~/.*\.ogz/) {
			mapCount++
		}
		if (mapCount == 1) {
			def backupBase = new File(plexusDir, "maps/${mapFile.getName()}").getAbsolutePath()
			def backup = new File(backupBase)
			def backupCount = 0

			while (backup.exists()) {
				backup = new File(backupBase + "-${++backupCount}")
			}
			backup.getParentFile().mkdirs()
			Tools.copyAll(mapFile.getParentFile(), backup)
			P2PMudFile.storeDir(cacheDir, backup, [
				receiveResult: {result ->
					def name = mapFile.getName()
					def id = result.getId().toStringFull()

					addMap(name, id)
					peer.broadcastCmds(plexusTopic, ["addMap $name $id"] as String[])
					sauer('echo', "echo Stored dir: $result" as String)
					dumpCommands()
				},
				receiveException: {exception ->
					exception.printStackTrace()
				}
			] as Continuation);
		} else {
			def prefixLen = sauerDir.getAbsolutePath().length() + 1
			def errTitle = "Error, There is more than one map in the folder ${parent.getAbsolutePath().substring(prefixLen)}"
			def errMsg = "Please put this map and its requirements in its own map folder"

			println "$errTitle: $errMsg"
			sauer('msg', "showmessage [$errTitle] [$errMsg]")
			dumpCommands()
		}
	}
}
