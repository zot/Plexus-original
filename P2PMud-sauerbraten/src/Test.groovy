import rice.Continuation.MultiContinuation
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
	def mapTopic
	def plexusTopic
	def presenceLock = new Object()
	def playersDoc
	def idToMap = [:]
	def peerToSauerIdMap = [:]
	
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
				if (topic == null && cmd == null) {
					id = id.toStringFull()
					removePlayer(id)
					peer.broadcastCmds(plexusTopic, "removePlayer $id")
				} else {
					pastryCmd = cmd
					cmd.msgs.each {pastryCmds.invoke(it)}
				}
			} as P2PMudCommandHandler,
			{
				//sauer('peers', "peers ${peer.getNeighborCount()}")
				//dumpCommands()
			},
			args[2..-1] as String[])
		peer = P2PMudPeer.test
		plexusTopic = peer.subscribe(peer.buildId(PLEXUS_KEY), null)
		if (peer.node.getLeafSet().getUniqueCount() == 1) {
			initBoot()
		} else {
			initJoin()
		}
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
			sauer('newmap', 'tc_allowedit 1; newmap; musicvol 0')
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
						sauer('delcube', "selcube $x $y 460 1 1 2 32 5; delcube")
						dumpCommands()
						
						if (blocks[i][j] == 'e') {
							
						} else if (blocks[i][j] == 's') {
							
						}
					}
				}
			}
			
			sauer('tex', 'texturereset; setshader stdworld; texture 0 "egyptsoc/lig_b064-02d.jpg"; texture 0 "egyptsoc/stone01a.jpg"; texture 0 "tech1soc/sqrlig03bc.jpg"; ')
			sauer("texture", "selcube 0 0 480 2 2 2 512 0; tc_settex 1 1")
			sauer('finished', 'tc_allowedit 0')
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
							Tools.stackTrace(ex)
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
	 	updateFriendList()
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
	def uniqify(name) {
		"$name-${TIME_STAMP.format(new Date())}"
	}
	def loadMap(name, id) {
		def tmpDir = new File(plexusDir, "maps/loadMap-${System.currentTimeMillis()}")

		println "Loading map: ${id}"
		if (id instanceof String) {
			id = Id.build(id)
		}
		P2PMudFile.fetchDir(id, cacheDir, tmpDir, [
			receiveResult: {
				def mapDir = new File(plexusDir, "maps/$name")
				if (mapDir.exists()) {
					def count = 0
					def backupDir = new File(plexusDir, "backups")
					if (!backupDir.exists()) backupDir.mkdirs()
					
					def backup = new File(backupDir, "$name")
					while (backup.exists()) {
						count++
						backup = new File(backupDir, "$name-$count")
					}
					if (!mapDir.renameTo(backup)) {
						sauer('msg', "showmessage [Could not backup old map dir: backup]")
						dumpCommands()
						Tools.deleteAll(tmpDir)
						return
					}
				}
				if (!tmpDir.renameTo(mapDir)) {
					err("Couldn't rename temporary load map dir to $mapDir", new Exception())
				} else {
					def mapPath = Tools.subpath(new File(sauerDir, "packages"), mapDir)

					println "Retrieved map from PAST: $mapDir, executing: map [$mapPath/$name]"
					sauer('load', "echo loading new map: [$mapPath/$name]; map [$mapPath/$name]")
					dumpCommands()
				}
			},
			receiveException: {err("Couldn't load map: $id", it)}
		] as Continuation, false)
	}
	def err(msg, err) {
		println(msg)
		Tools.stackTrace(err);
	}
	def initBoot() {
		def maps = new File(plexusDir, "mapsdoc")

		if (maps.exists()) {
			def props = Tools.properties(maps)
			def newIds = [:]

			for (map in props) {
				def entry = map.value.split(' ')

				newIds[map.key] = [entry[0], entry.length == 2 ? entry[1] : entry[1..-1].join(' '), 0]
				println "$map.key: $map.value"
				println "$map.key: ${newIds[map.key]}"
			}
			setMapsDoc(newIds, false)
		} else {
			setMapsDoc([:], true)
		}
		updatePlayer(peer.nodeId.toStringFull(), [name, null])
		storeCache()
	}
	def initJoin() {
		peer.anycastCmds(plexusTopic, "sendMaps")
		peer.anycastCmds(plexusTopic, "sendPlayers")
	}
	def storeCache() {
		if (cacheDir.exists()) {
			def files = []
			def count = 0
	
			cacheDir.eachFile {
				it.eachFile {
					files.add(it)
				}
			}
			def mcont = new MultiContinuation([
				receiveResult: {
					println "FINISHED PUSHING CACHE"
					sauer('msg', 'showmessage Ready [Finished pushing cache in PAST]')
					dumpCommands()
				},
				receiveException: {
					println "FAILED TO PUSH CACHE"
					err("Error pushing cache in PAST", it)
				}
			] as Continuation, files.size())
			for (file in files) {
				peer.wimpyStoreFile(cacheDir, file, mcont.getSubContinuation(count++), false, true)
			}
		}
	}
	def setPlayersDoc(doc) {
		synchronized (presenceLock) {
			playersDoc = doc
		}
		updateFriendList()
	}
	def updateMyPlayerInfo() {
		def id = mapTopic?.getId()?.toStringFull()

		println "UPDATING PLAYER INFO"
//		after we get the players list, send ourselves out
		def node = peer.nodeId.toStringFull()
		peer.broadcastCmds(plexusTopic, "updatePlayer $node $name $id")
		updatePlayer(node, [name, id])
		println "BROADCAST: updatePlayer $node $name $id"
	}
	def updatePlayer(node, info) {
		if (mapTopic) {
			synchronized (presenceLock) {
				playersDoc = playersDoc ?: [:]
				//println info
				playersDoc[node] = info
				// if they aren't on our map now, see if we need to delete them from sauer
				if (info[1] != mapTopic.getId().toStringFull()) removePlayerFromSauerMap(node)
			}
		}
		updateFriendList()
	}
	def removePlayer(node) {
		synchronized (presenceLock) {
			playersDoc = playersDoc ?: [:]
			//println "Going to remove player: $node"
			playersDoc.remove(node)
			removePlayerFromSauerMap(node)
		}
		updateFriendList()
	}
	def removePlayerFromSauerMap(node) {
		if (peerToSauerIdMap[node]) {
			def sauerId = peerToSauerIdMap[node]
			def who = names[sauerId]
			println "Going to remove player from sauer: $sauerId"
			sauer('delplayer', "echo [Player $who has left this world.]; deleteplayer $sauerId")
			peerToSauerIdMap.remove(node)
			names.remove(sauerId)
			ids.remove(who)
		}
	}
	def updateFriendList() {
		if (!peer?.nodeId) return
		synchronized (presenceLock) {
			def friendGui = 'newgui Friends [\n'
			def cnt = 1
			def mapCnt = 0
			def id = peer.nodeId.toStringFull()
			def mname = "Limbo"

			updateMapGui()
			for (player in playersDoc) {
				if (player.key != id) {
					def info = player.value
					println info
					def who = info[0]
					def map = (!info[1] || info[1] == 'null') ? 'none' : idToMap[info[1]][1]
					friendGui += "guibutton [$who ($map)] [echo $player.key ]\n"
					++cnt
				}
			}
			if (cnt == 1) friendGui += 'guitext "Sorry, no friends are online!"\n'
				friendGui += "guibar\n guibutton Close [cleargui]\n"
			if (mapTopic) {
				def mid = mapTopic.getId().toStringFull()

				mname = idToMap[mid][1]
				friendGui += "guitab $mname\n"
				mapCnt = 1
				for (player in playersDoc) {
					if (player.key != id) {
						def info = player.value
						def who = info[0]
						
						if (info[1] == mid) {
							friendGui += "guibutton [$who] [echo $player.key]\n"
							++mapCnt
						}
					}
				}
				if (mapCnt == 1) friendGui += "guitext [Sorry, no friends are connected to $mname!]\n"
				println "MAPCNT: $mapCnt"
				friendGui += "guibar\n guibutton Close [cleargui]\n"
			}
			friendGui += "]; peers $cnt; tc_mapcount $mapCnt; tc_loadmsg $mname"
			sauer('friend', cvtNewlines(friendGui))
			dumpCommands()
		}
	}
	def addMap(topic, tree, name) {
		synchronized (presenceLock) {
			idToMap[topic] = [tree, name, 0]
		}
		updateMapGui()
		saveMapsDoc()
	}
	def setMapsDoc(newIds, save) {
		synchronized (presenceLock) {
			idToMap = newIds
			updateMapGui()
			if (save) {
				saveMapsDoc()
			}
		}
	}
	def saveMapsDoc() {
		def maps = [:] as Properties

		for (map in idToMap) {
			maps[map.key] = map.value[0..1].join(' ')
		}
		Tools.store(maps, new File(plexusDir, 'mapsdoc'), "Maps document")
	}
	def updateMapGui() {
		for (world in idToMap) {
			world.value[2] = 0
		}
		for (player in playersDoc) {
println "player.value: $player.value"
			def map = player.value[1]

			if (map && map != 'null') {
				idToMap[map][2]++
			}
		}
		def mapsGui = "newgui Worlds ["
		def ents = []
		for (world in idToMap) {
			ents.add([world.value[1], world.key, world.value[2]])
		}
		ents.sort {a, b -> a[0].compareTo(b[0])}
		for (world in ents) {
			mapsGui += "guibutton [${world[0]} (${world[2]})] [remotesend connectWorld ${world[1]}]\n"
		}
		mapsGui += "]\n"
		sauer('maps', cvtNewlines(mapsGui))
		dumpCommands()
	}
	def connectWorld(id) {
		def entry = idToMap[id]

		if (entry) {
			println "CONNECTING TO WORLD: entry[1] ($id)"
			if (mapTopic) {
				peer.unsubscribe(mapTopic)
			}
			mapTopic = peer.subscribe(Id.build(id), [
				receiveResult: {topic ->
					mapTopic = topic
					println "SUBSCRIBED TO $topic"
					loadMap(entry[1], entry[0])
				},
				receiveException: {exception -> err("Couldn't subscribe to topic: ", id)}
			] as Continuation)
		}
	}
	def initiatePush(mapName, update) {
		def mapFile = new File(sauerDir, "packages/$mapName")
		mapFile.getParentFile().mkdirs()
		sauer('push', "savemap $mapName; remotesend pushMap $mapName $update")
		dumpCommands()
	}
	def pushMap(mapName, replace) {
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
					def tree = result.getId().toStringFull()
					def topic = replace && mapTopic ? mapTopic.getId().toStringFull() : tree

					addMap(topic, tree, name)
					peer.broadcastCmds(plexusTopic, ["addMap $topic $tree $name"] as String[])
					sauer('echo', "echo Stored dir: $result" as String)
					dumpCommands()
				},
				receiveException: {exception ->	Tools.stackTrace(exception)}
			] as Continuation);
		} else {
			def errTitle = "Error, There is more than one map in the folder ${Tools.subpath(sauerDir, parent)}"
			def errMsg = "Please put this map and its requirements in its own map folder"

			println "$errTitle: $errMsg"
			sauer('msg', "showmessage [$errTitle] [$errMsg]")
			dumpCommands()
		}
	}
	def copyWorld(newName) {
		if (mapTopic) {
			def topic = peer.randomId().toStringFull()
			def oldEntry = idToMap[mapTopic.getId().toStringFull()]

			addMap(topic, oldEntry[0], oldEntry[1])
			peer.broadcastCmds(plexusTopic, ["addMap $topic ${oldEntry[0]}, ${oldEntry[1]}"] as String[])
		} else {
			sauer('msg', "showmessage [Error] [No current map]")
		}
	}
}
