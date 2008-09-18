import org.jdesktop.swingx.painter.GlossPainter
import java.awt.Color
import org.jdesktop.swingx.border.DropShadowBorder
import groovy.swing.SwingXBuilder
import java.awt.Font
import p2pmud.Tools
import static p2pmud.Tools.*
import java.awt.event.ItemEvent
import java.util.concurrent.Executors
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel
import javax.swing.UIManager
import p2pmud.CloudProperties
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
import DFMapBuilder
import GroovyFileFilter

public class Plexus {
	def socket = null
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
	def playerObj = new Player()
	def peerId
	def sauerDir
	def plexusDir
	def cacheDir
	def mapDir
	def mapPrefix = 'packages/dist/storage'
	def peer
	def mapname
	def costume
	def mapTopic
	def mapIsPrivate
	/** cloudProperties is the shared properties object for Plexus
	 * its keys are path-strings, representing information organized in
	 * a tree
	 */
	def cloudProperties
	def plexusTopic
	def presenceLock = new Lock('presence')
	def playerCount = [:]
	def peerToSauerIdMap = [:]
	def triggerLambdas = [:]
	def portals = [:]
	def receivedCloudPropertiesHooks = [
		{updateMyPlayerInfo()}
	]
	def executor = Executors.newSingleThreadExecutor()
	def neighborField
	def playerListeners = [:]
	def maps
	def mapCombo
	def mapPlayers
	def mapPlayersCombo
	def downloadPanel
	def downloadProgressBar
	def uploadCountField
	def downloadCountField
	def loadTypeField
	def followingPlayer
	def cotumeUploadField
	def tumes
	def tumesCombo
	def fileQ = []
	def executorThread
	def pendingDownloads = [] as Set
	def uploads = 0
	def downloads = 0
	def headless = false
	def cloudFields = [:]

	def static sauerExec
	def static soleInstance
	def static TIME_STAMP = new SimpleDateFormat("yyyyMMdd-HHmmsszzz")
	def static final PLEXUS_KEY = "Plexus: main"
	def static final WORLDS_KEY = "Plexus: worlds"
	def static final SPHINX_KEY = "Plexus: sphinx"

	public static continuation(args, parent = null) {
		Tools.continuation(args, parent)
	}
	public static void main(String[] a) {
		if (a.length < 2) {
			println "Usage: Plexus port name pastryArgs"
			System.exit(1);
		}
		new Plexus()._main(a)
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
	def checkExec() {
		if (executorThread != Thread.currentThread()) {
			new Exception("Not running in executor thread").printStackTrace()
		}
		assert executorThread == Thread.currentThread()
	}
	def exec(block) {
		executor.submit(block)
	}
	def _main(args) {
		exec {
			executorThread = Thread.currentThread()
		}
		soleInstance = this
		headless = LaunchPlexus.props.headless == '1'
		if (!headless) {
			sauerDir = System.getProperty("sauerdir");
			if (!verifySauerdir(sauerDir)) {
				usage("sauerdir must be provided")
			} else if (!name) {
				usage("name must be provided")
			}
			sauerDir = new File(sauerDir)
		} else {
			sauerDir = new File('duh').getAbsoluteFile().getParentFile()
		}
		name = args[1]
		plexusDir = new File(sauerDir, "packages/plexus")
		cloudProperties = new CloudProperties(this, new File(plexusDir, "cache/$name/cloud.properties"))
		cloudProperties.persistentPropertyPattern = ~'(map|privateMap|costume)/..*'
		cloudProperties.privatePropertyPattern = ~'(privateMap)/..*'
		cacheDir = new File(plexusDir, "cache/$name/files")
		mapDir = new File(plexusDir, "cache/$name/maps")
		mapDir.mkdirs()
		def pastStor = new File(plexusDir, "cache/$name/PAST")
		deleteAll(pastStor)
		pastStor.mkdirs()
		System.setProperty('past.storage', pastStor.getAbsolutePath())
		if (!headless) {
			cloudProperties.setPropertyHooks[~'player/..*'] = {key, value, oldValue ->
				if (mapTopic) {
					def pid = key.substring('player/'.length())
					def pl = getPlayer(pid)

					if (pl.map != mapTopic.getId().toStringFull()) {
						removePlayerFromSauerMap(pl.id)
					} else if (oldValue) {
						def oldPl = getPlayer(pid, oldValue)

						if (oldPl.costume != pl.costume) {
							println "Costume changed.  Loading new costume for $pl"
							loadCostume(pl)
						}
					}
				}
			}
			cloudProperties.setPropertyHooks[~'map/..*'] = {key, value, oldValue ->
				def map = getMap(key.substring('map/'.length()))

				if (!oldValue) {
					playerCount[map.id] = 0
				}
				if (map.id == mapTopic?.getId()?.toStringFull()) {
					loadMap(map.name, map.dir)
				}
			}
			cloudProperties.setPropertyHooks[~'privateMap/..*'] = {key, value, oldValue ->
				def map = getMap(key.substring('privateMap/'.length()))

				if (!oldValue && pastryCmd) {
					def player = getPlayer(pastryCmd.from.toStringFull())

					playerCount[map.id] = 0
					sauer('private', "tc_msgbox [You received the key to a private world, $map.name: $map.id] [from $player.name ($player.id)]")
				}
				if (key == mapTopic?.getId()?.toStringFull()) {
					loadMap(map.name, map.id)
				}
			}
			cloudProperties.removePropertyHooks[~'player/..*'] = {key, value ->
				removePlayerFromSauerMap(key.substring('player/'.length()))
			}
			cloudProperties.changedPropertyHooks.add {
				updateFriendList()
				updateMapGui()
				updateCostumeGui()
			}
			if ((LaunchPlexus.props.sauer_mode ?: 'launch') == 'launch') launchSauer();
			//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());
			try {
//			   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
			   UIManager.setLookAndFeel("org.jdesktop.swingx.plaf.nimbus.NimbusLookAndFeel");
			} catch (Exception e) {}
			buildPlexusGui()
			start(args[0])
		} else {
			cloudProperties.saveFilter = {prop -> !pendingDownloads.contains(prop)}
			cloudProperties.setPropertyHooks[~'map/..*'] = {key, value, oldValue ->
				def map = getMap(key.substring('map/'.length()))

				println "RECEIVED MAP NOTIFICATION, DOWNLODING..."
				fetchAndSave("map $map.name", key, map.dir, "maps/$map.dir")
			}
			cloudProperties.setPropertyHooks[~'costume/..*'] = {key, value, oldValue ->
				def costume = getCostume(key.substring('costume/'.length()))

				println "RECEIVED MAP NOTIFICATION, DOWNLODING..."
				fetchAndSave("costume", key, costume.dir, "costumes/$costume.dir")
			}
		}
		P2PMudPeer.verboseLogging = LaunchPlexus.props.verbose_log == '1'
		P2PMudPeer.logFile = new File(plexusDir, "cache/$name/plexus.log")
		P2PMudPeer.main(
			{id, topic, cmd ->
				try {
					exec {
						if (topic == null && cmd == null) {
							id = id.toStringFull()
							transmitRemoveCloudProperty("player/$id")
						} else {
							pastryCmd = cmd
							cmd.msgs.each {line ->
								pastryCmds.invoke(line)
							}
							pastryCmd = null
						}
					}
				} catch (Exception ex) {
					err("Problem executing command: " + cmd, ex)
				}
			} as P2PMudCommandHandler,
			{
				//sauer('peers', "peers ${peer.getNeighborCount()}")
				//dumpCommands()
			},
			args[2..-1] as String[])
		peer = P2PMudPeer.test
		peerId = peer.nodeId.toStringFull()
//		println "Node ID: $peerId}"
println "SAVED NODE ID: $LaunchPlexus.props.nodeId"
		if (!LaunchPlexus.props.nodeId) {
			LaunchPlexus.props.nodeId = peerId
			println "SAVING NEW NODE ID: $LaunchPlexus.props.nodeId"
			LaunchPlexus.saveProps()
		}
		names = [p0: peerId]
		ids[peerId] = 'p0'
		plexusTopic = peer.subscribe(peer.buildId(PLEXUS_KEY), null)
		println "execing init..."
		exec {
			if (peer.node.getLeafSet().getUniqueCount() == 1) {
				println "initBoot"
				initBoot()
			} else {
				println "initJoin"
				initJoin()
			}
		}
	}
	def fetchAndSave(type, prop, id, location) {
		pendingDownloads.add(prop)
		showDownloadProgress(0, 16)
		fetchDir(id, new File(plexusDir, "cache/$name/$location"), receiveResult: {r ->
			exec {
				println "RECEVED ${type.toUpperCase()}, CHECKPOINTING CLOUD PROPS"
				pendingDownloads.remove(prop)
				cloudProperties.save()
				updateDownloads()
				clearDownloadProgress()
			}
		}, receiveException: {ex -> err("Could not fetch data for $type: $id -> ${new File(plexusDir, "cache/$name/$location")}", ex)})
	}
	def buildPlexusGui() {
		swing = new SwingXBuilder()
		swing.build {
			def makeTitlePainter = {
					swing.compoundPainter() {
			            swing.mattePainter(fillPaint:Color.BLACK)
			            swing.glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position:GlossPainter.GlossPosition.TOP)
			        }
				}
			def field = {lbl, key ->
				swing.label(text: lbl)
				fields[key] = swing.textField(actionPerformed: {sauerEnt(key)}, focusLost: {sauerEnt(key)}, constraints: 'wrap, growx')
			}
			def f = swing.frame(title: 'Plexus: ' + LaunchPlexus.props.name, size: [500, 500], windowClosing: {System.exit(0)}, layout: new MigLayout('fill'), pack: true, show: true) {
				swing.titledPanel(title: 'Plexus Properties', titlePainter: makeTitlePainter(), border: new DropShadowBorder(Color.BLACK, 15)) {
					swing.panel(layout: new MigLayout('fillx')) {
						swing.label(text: "Node id: ")
						swing.label(text: LaunchPlexus.props.nodeId ?: "none", constraints: 'wrap, growx')
						swing.label(text: "Neighbors: ")
						swing.panel(layout: new MigLayout('fill, ins 0'), constraints: 'spanx,wrap,growx') {
							swing.button(text: "Update Neighbor List", actionPerformed: {updateNeighborList()})
							neighborField = swing.label(text: 'none', constraints: 'wrap, growx')
						}
						swing.label(text: "Command: ")
						fields.cmd = swing.textField(actionPerformed: {cmd()}, constraints: 'wrap, growx')
						swing.tabbedPane(constraints: 'spanx,width 100%,growy,wrap') {
							swing.panel(name: 'Commands', layout: new MigLayout('fill')) {
								swing.label(text: 'Generation')
								swing.panel(layout: new MigLayout('fill, ins 0'), constraints: 'growx,wrap') {
									swing.button(text: "Launch 3D", actionPerformed: {launchSauer()})
									swing.button(text: "Generate Dungeon", actionPerformed: {generateDungeon()})
									swing.button(text: "Load DF Map", actionPerformed: {loadDFMap()})
									swing.panel(constraints: 'growx,wrap')
								}
								swing.label(text: "Current Map: ")
								mapCombo = swing.comboBox(editable: false, actionPerformed: {
									exec {
										if (mapCombo && mapCombo.selectedIndex > -1) {
											connectWorld(mapCombo.selectedIndex == 0 ? null : maps[mapCombo.selectedIndex - 1].id)
										}
									}
								}, constraints: 'wrap')
								swing.label(text: 'Choose Costume')
								tumesCombo = swing.comboBox(editable: false, actionPerformed: {
									exec {
										if (tumesCombo && tumesCombo.selectedIndex > -1) {
											if (tumesCombo.selectedIndex) {
												def tume = tumes[tumesCombo.selectedIndex - 1]
		
												useCostume(tume.name, tume.dir)
											}
										}
									}
								}, constraints: 'wrap')
								swing.label(text: "Follow player: ")
								mapPlayersCombo = swing.comboBox(editable: false, actionPerformed: {
									if (mapPlayersCombo && mapPlayersCombo.selectedIndex > -1) {
										followingPlayer = mapPlayersCombo.selectedIndex == 0 ? null : mapPlayers[mapPlayersCombo.selectedIndex - 1]
		println "NOW FOLLOWING: ${followingPlayer?.name}"
									}
								}, constraints: 'wrap')
								swing.button(text: 'Upload Costume', actionPerformed: {
									exec {
										if (costumeUploadField.text) {
											pushCostumeDir(costumeUploadField.text as File)
										}
									}
								})
								swing.panel(constraints: 'growx,wrap', layout: new MigLayout('fill,ins 0')) {
									costumeUploadField = swing.textField(constraints: 'growx', actionPerformed: {
										exec {pushCostumeDir(costumeUploadField.text as File)}
									})
									swing.button(text: '...', actionPerformed: {
										exec {
											def file = chooseFile("Choose a model to upload", costumeUploadField, "Costumes", "")
		
											if (file) {
												pushCostumeDir(file)
											}
										}
									})
								}
								swing.panel(constraints: 'growy,wrap')
								downloadPanel = swing.panel(constraints: 'growx,spanx,wrap', layout: new MigLayout('fill,ins 0'), enabled: false) {
									swing.label(text: ' Pending Uploads: ')
									uploadCountField = swing.label(text: '0')
									swing.label(text: ' Pending Downloads: ')
									downloadCountField = swing.label(text: '0')
									swing.label(text: 'Current ')
									loadTypeField = swing.label(text: 'Upload')
									downloadProgressBar = swing.progressBar(constraints: 'growx', minimum: 0, maximum: 100)
								}
							}
							swing.panel(name: 'Stats', layout: new MigLayout('fill,ins 0')) {
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
								swing.panel(constraints: 'growy,wrap')
							}
							swing.panel(name: 'Cloud', layout: new MigLayout('fill,ins 0')) {
								swing.label(text: 'Neighbors: ')
								cloudFields.neighbors = swing.label(constraints: 'growx,wrap')
								swing.label(text: 'Cloud Properties', constraints: 'growx,spanx,wrap')
								cloudFields.properties = swing.textPane(constraints: 'grow,span,wrap')
							}
						}
					}
				}
			}
		}
	}
	def updateDownloads() {
		downloadCountField.text = downloads as String
		uploadCountField.text = uploads as String
	}
	def chooseFile(message, field, filterName, filterRegexp) {
		def ch = new JFileChooser();

		if (field.text) {
			ch.setSelectedFile(field.text as File)
		}
		ch.setDialogTitle(message);
		ch.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES)
		ch.setFileFilter(new GroovyFileFilter(filterName) {it.isDirectory() || it.name ==~ filterRegexp})
		def result = ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION ? ch.getSelectedFile() : null
		if (result) {
			field.text = result.getAbsolutePath()
		}
		return result
	}
	def updateNeighborList() {
		try {
			neighborField.text = String.valueOf(peer.getNeighborCount())
		} catch (Exception ex) {}
	}
	def launchSauer() {
		if (sauerExec) {
			def env = []
			def winderz = System.getProperty('os.name').toLowerCase() ==~ /.*windows.*/

			for (vars in System.getenv()) {
				if (winderz && vars.key.equalsIgnoreCase('path')) {
					env.add("$vars.key=$vars.value;$sauerDir\\bin")
				} else {
					env.add("$vars.key=$vars.value")
				}
			}
			env = env as String[]
			println ("Going to exec $sauerExec from $sauerDir with env: $env")
			Runtime.getRuntime().exec(sauerExec,  env, sauerDir)
		}
	}
	def loadDFMap() {
		def ch = new JFileChooser();
		ch.setDialogTitle("Please choose the DF map to load");
		ch.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES)
		ch.setFileFilter(new GroovyFileFilter("DF ASCII MAPS (*.txt)") {it.isDirectory() || it.name.toLowerCase().endsWith(".txt")})
		if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			Thread.start {
				if (ch.getSelectedFile().isFile()) {
					def dir = ch.getSelectedFile().getAbsolutePath();
				
					def df = new DFMapBuilder()
					df.buildMap(this, dir)
				}
			}
		}
	}

	// generate a random dungeon
	def generateDungeon() {
		println ("Going to generate dungeon")
		Thread.start {
			exec {
				sauer('newmap', 'if (= 1 $editing) [ edittoggle ]; tc_allowedit 1; thirdperson 0; newmap; musicvol 0')
				sauer('models', 'mapmodelreset ; mmodel tc_door ')
				dumpCommands()
			}
			def dungeon = new Dungeon(6, 6, 3, 1)

		    dungeon.generate_maze();

			def blocks = dungeon.convertTo3DBlocks()

			for (def i = 0; i < dungeon.blockRows; ++i) {
				for (def j = 0; j < dungeon.blockCols; ++j) {
					def b = blocks[i][j] 
					if (b != 'X') {
						def x = i * 32
						def y = j * 32
						def wx = x - 32, wy = y - 32
						//println "x: $x y: $y"
						def h = (b == ' ' || b == 'l') ? 2 : 1
						exec {
							if (b == 'z') {
								sauer('secret', "selcube $x $y 430 1 1 $h 32 5; editmat noclip")
							} else {
								sauer('delcube', "selcube $x $y 430 1 1 $h 32 5; delcube")
							}
							if (b == 'e') {
								sauer('door', "selcube $x $y 430 1 1 1 32 4; ent.yaw p0 180;  entdrop 3; newent mapmodel 0 6")
							}
							else if (b == 's') {
								sauer('door', "selcube $x $y 430 1 1 1 32 4; ent.yaw p0 90;  entdrop 3; newent mapmodel 0 6")
							}
							else if (b == 'l') {
								sauer('light', "selcube $x $y 450 1 1 1 32 4;  entdrop 2; newent light 128 255 255 255")
							}
							dumpCommands()
						}
					}
				}
			}
			
			exec {
				sauer('tex', 'texturereset; setshader stdworld; exec packages/egyptsoc/package.cfg ')
				sauer("texture", "selcube 0 0 480 2 2 2 512 4; tc_settex 35 1")
				sauer("texture2", "tc_settex 37 0; selcube 0 0 480 2 2 2 512 5; tc_settex 51 0")
				sauer("texture3", "selcube 0 0 470 512 512 1 16 5; tc_settex 7 1")
			}
			
			for (def i = 0; i < dungeon.blockRows; ++i) {
				for (def j = 0; j < dungeon.blockCols; ++j) {
					def b = blocks[i][j] 
					if (b != 'X') {
						def x = i * 32
						def y = j * 32
						def wx = x - 32, wy = y - 32
						exec {
							if (b == 'e') {
								sauer('wall1', "selcube $wx $wy 430 3 3 1 32 0; tc_settex 56 0")
								sauer('wall2', "selcube $wx $wy 430 3 3 1 32 1; tc_settex 56 0")
							}
							else if (b == 's') {
								sauer('wall1', "selcube $wx $wy 430 3 3 1 32 2; tc_settex 56 0")
								sauer('wall2', "selcube $wx $wy 430 3 3 1 32 3; tc_settex 56 0")
							}
							dumpCommands()
						}
					}
				}
			}
			
			exec {
				sauer("spawn", "selcube 32 32 416 1 1 1 32 5; ent.yaw p0 135; newent playerstart; tc_respawn p0")
				sauer('finished', 'remip; calclight 3; tc_allowedit 0; thirdperson 1')
				dumpCommands()
			}
		};
	}
	//Plexus.bindLevelTrigger(35, 'remotesend levelTrigger 35 $more $data') {println "duh"} remotesend levelTrigger 35
	def bindLevelTrigger(trigger, lambda) {
		if (!lambda) println "Error! Trigger lambda is null!"
		trigger = Integer.parseInt(trigger)
		triggerLambdas[trigger] = lambda
		sauer('trigger', "level_trigger_$trigger = [ remotesend levelTrigger $trigger ]")
		dumpCommands()
	}
	def levelTrigger(trigger) {
		trigger = Integer.parseInt(trigger)
		println "sauer trigger: $trigger"
		if (triggerLambdas[trigger]) triggerLambdas[trigger](trigger)
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
				socket = sock.accept {
					println("Got connection from sauerbraten...")
					output = it.getOutputStream()
					exec {init()}
					try {
						it.getInputStream().eachLine {line ->
							exec {
								try {
									exec {sauerCmds.invoke(line)}
								} catch (Exception ex) {
									err("Problem executing sauer command: " + it, ex)
								}
							}
						}
					} catch (Exception ex) {}
					try {it.shutdownInput()} catch (Exception ex) {}
					try {it.shutdownOutput()} catch (Exception ex) {}
					println "Disconnect"
				};
			}
		}
	}
	def sauer(key, value) {
		checkExec()
		pendingCommands[key] = value
	}
	def dumpCommands() {
		checkExec()
		if (!pendingCommands.isEmpty()) {
			if (socket?.isConnected()) {
				def out = pendingCommands.collect{it.value}.join(";") + '\n'
//				println out
				try {
					output << out
					output.flush()
				} catch (SocketException ex) {
					try {socket.shutdownInput()}catch(Exception ex2){}
					try {socket.shutdownOutput()}catch(Exception ex2){}
					socket = null
				}
			}
			pendingCommands = [:]
		}
	}
	def sauerEnt(label) {
		if (fields[label]?.text && fields[label].text[0]) {
			def cmd = "ent.$label ${ids[peerId]} ${fields[label].text}"
			exec {
				sauer(label, cmd)
				dumpCommands()
			}
		}
	}
	def cmd() {
		if (fields.cmd.text && fields.cmd.text[0]) {
			def txt = fields.cmd.text

			fields.cmd.text = ""
			exec {
				sauer('cmd', fields.cmd.text)
				dumpCommands()
			}
		}
	}
	def init() {
	 	sauer('init', [
			"alias p2pname [$name]",
			"cleargui 1",
			"showgui Plexus",
			'echo INIT'
		].join(';'))
	 	dumpCommands()
	 	updateFriendList()
	 	updateMapGui()
	 	updateCostumeGui()
	}
	def broadcast(cmds) {
		checkExec()
		if (peer) peer.broadcastCmds(mapTopic, cmds as String[])
	}
	def anycast(cmds) {
		checkExec()
		if (peer) peer.anycastCmds(mapTopic, cmds as String[])
	}
	def send(id, cmds) {
		checkExec()
		if (id instanceof String) {
			id = Id.build(id)
		}
		if (peer) peer.sendCmds(id, cmds as String[])
	}
	def cvtNewlines(str) {
		println "${str.replaceAll(/\n/, ';')}"
		return str.replaceAll(/\n/, ';')
	}
	def uniqify(name) {
		"$name-${TIME_STAMP.format(new Date())}"
	}
	def loadMap(name, id, cont = null) {
		def dir = new File(mapDir, id)

		println "Loading map: ${id}"
		if (id instanceof String) {
			id = Id.build(id)
		}
		fetchDir(id, dir, receiveResult: {result ->
			def mapPath = subpath(new File(sauerDir, "packages"), dir)

			println "Retrieved map from PAST: $dir, executing: map [$mapPath/map]"
			sauer('load', "echo loading new map: [$mapPath/map]; tc_loadmsg [$name]; map [$mapPath/map]")
			dumpCommands()
			if (cont) {cont.receiveResult(result)}
		}, receiveException: {ex ->
			if (cont) {
				cont.receiveException(ex)
			} else {
				err("Couldn't load map: $id", ex)
			}
		})
	}
	def selectMap() {
		mapCombo.selectedItem = mapTopic ? getMap(mapTopic.getId().toStringFull()).name : ''
	}
	def err(msg, err) {
		println(msg)
		err.printStackTrace()
		stackTrace(err)
	}
	def initBoot() {
		def docFile = new File(plexusDir, "cache/$name/cloud.properties")

		if (docFile.exists()) {
			cloudProperties.load()
		}
		updateMyPlayerInfo()
		storeCache()
	}
	def initJoin() {
		checkExec()
		deleteAll(cacheDir)
		peer.anycastCmds(plexusTopic, "sendCloudProperties")
	}
	def storeFile(cont, file, mutable = false, cacheOverride = false) {
		def total = P2PMudFile.estimateChunks(file.length)
		def chunk = 0

		uploads++
		showUploadProgress(0, 16)
		updateDownloads()
		queueIo(cont, {uploads--; updateDownloads(); clearUploadProgress()}) {chain -> peer.wimpyStoreFile(cacheDir, file, {showUploadProgress(chunk++, total)}, chain, mutable, cacheOverride)}
	}
	def storeDir(cont, dir) {
		uploads++
		showUploadProgress(0, 16)
		updateDownloads()
		queueIo(cont, {uploads--; updateDownloads(); clearUploadProgress()}) {chain -> P2PMudFile.storeDir(cacheDir, dir, {chunk, total -> showUploadProgress(chunk, total)}, chain)}
	}
	def fetchFile(cont, id) {
		def chunk = 0

		downloads++
		showDownloadProgress(0, 16)
		updateDownloads()
		queueIo(cont, {downloads--; updateDownloads(); clearDownloadProgress()}) {chain -> peer.wimpyGetFile(id, cacheDir, {total -> showDownloadProgress(chunk++, total)}, chain)}
	}
	def fetchDir(cont, id, dir, mutable = false) {
		downloads++
		showDownloadProgress(0, 16)
		updateDownloads()
		queueIo(cont, {downloads--; updateDownloads(); clearDownloadProgress()}) {chain -> P2PMudFile.fetchDir(id, cacheDir, dir, {chunk, total -> showDownloadProgress(chunk, total)}, chain, mutable)}
	}
	def queueIo(cont, completedBlock, block) {
		checkExec()
		if (fileQ.empty) {
			println "EXECUTING"
			block(ioContinuation(cont, completedBlock))
		} else {
			println "QUEUING"
			fileQ.add({println "EXECUTING QUEUED"; block(ioContinuation(cont, completedBlock))})
		}
	}
	def ioContinuation(cont, completedBlock) {
		continuation(receiveResult: {r ->
			exec {
				println "DONE"
				completedBlock()
				cont.receiveResult(r)
				chainIo()
			}
		}, receiveException: {e ->
			exec {
				println "ERROR"
				completedBlock()
				cont.receiveException(e)
				chainIo()
			}
		})
	}
	def chainIo() {
		if (!fileQ.empty) {
			fileQ.remove(0)()
		}
	}
	def storeCache() {
		if (cacheDir.exists()) {
			def files = []

			println "STORING CACHE"
			deleteAll(new File(cacheDir, 'download'))
			cacheDir.eachFile {subDir ->
				subDir.eachFile {file ->
					files.add(file)
				}
			}
			serialContinuations(files, receiveResult: {
				println "FINISHED PUSHING CACHE"
//				sauer('msg', 'tc_msgbox Ready [Finished pushing cache in PAST]')
//				dumpCommands()
			}, receiveException: {
				println "FAILED TO PUSH CACHE"
				err("Error pushing cache in PAST", ex)
			}) {file, chain ->
				println "STORING FILE: $file"
				storeFile(chain, file, false, true)
			}
		} else {
			println "NO CACHE TO STORE"
		}
	}
	def setCloudProperty(key, value) {
		cloudProperties[key] = value
	}
	def transmitSetCloudProperty(key, value) {
		checkExec()
		cloudProperties[key] = value
		peer.broadcastCmds(plexusTopic, ["setCloudProperty $key $value"] as String[])
		println "BROADCAST PROPERTY: $key=$value"
	}
	def transmitRemoveCloudProperty(key) {
		checkExec()
		cloudProperties.removeProperty(key)
		peer.broadcastCmds(plexusTopic, ["removeCloudProperty $key"] as String[])
		println "BROADCAST REMOVE PROPERTY: $key"
	}
	def removeCloudProperty(key) {
		checkExec()
		cloudProperties.removeProperty(key)
	}
	def receiveCloudProperties(props) {
		checkExec()
		cloudProperties.setProperties(props, true)
		receivedCloudPropertiesHooks.each {it()}
	}
	def getMap(id, entry = null) {
		checkExec()
		def privateMap = false
		
		if (!entry) {
			entry = cloudProperties["map/$id"]
		}
		if (!entry) {
			entry = cloudProperties["privateMap/$id"]
			privateMap = true
		}
		if (entry) {
			entry = entry.split(' ')
			return [
				id: id,
				dir: entry[0],
				name: entry[1..-1].join(' '),
				privateMap: privateMap
			]
		}
		return null
	}
	def getPlayer(id, entry = null) {
		def pl

		entry = entry ?: cloudProperties["player/$id"]
		if (entry) {
			entry = entry.split(' ')
			pl = [
				id: id,
				map: entry[0] == 'none' ? null : entry[0],
				costume: entry[1] == 'none' ? null : entry[1],
				name: entry[2..-1].join(' ')
			]

			if (pl.map == 'none') {
				pl.map = null
			}
		}
		return pl
	}
	def getCostume(id, entry = null) {
		if (!entry) {
			entry = cloudProperties["costume/$id"]
		}
		if (entry) {
			entry = entry.split(' ')
			return [
				dir: id,
				thumb: entry[0] == 'none' ? null : entry[0],
				type: entry[1],
				name: entry[2..-1].join(' ')
			]
		}
	}
	def updateMyPlayerInfo() {
		if (!headless) {
			def id = mapTopic?.getId()?.toStringFull()
	
			println "UPDATING PLAYER INFO"
	//		after we get the players list, send ourselves out
			def node = peer.nodeId.toStringFull()
	//TODO put tume in here and persist in props
			transmitSetCloudProperty("player/$node", "${id ?: 'none'} ${costume ?: 'none'} $name")
		}
	}
	def removePlayerFromSauerMap(node) {
		if (peerToSauerIdMap[node]) {
			def sauerId = peerToSauerIdMap[node]
			
			if (sauerId) {
				def who = sauerId ? getPlayer(names[sauerId]) : null

				if (who) {
					println "Going to remove player $who.name from sauer: $sauerId"
					sauer('msgplayer', "echo [Player $who.name has left this world.]")
					ids.remove(who.id)
				}
				names.remove(sauerId)
				sauer('delplayer', "deleteplayer $sauerId")
				updateMapGui()
				dumpCommands()
			}
			peerToSauerIdMap.remove(node)
		}
	}
	def newPlayer(name, id) {
		def who = getPlayer(pastryCmd.from.toStringFull())

		ids[who.id] = id
		++id_index
		names[id] = who.id
		peerToSauerIdMap[who.id] = id
		println peerToSauerIdMap
		sauer('prep', "echo [Welcome player $name to this world.]; createplayer $id $name")
		loadCostume(who)
	}
	def updateFriendList() {
		if (!peer?.nodeId) return
		synchronized (presenceLock) {
			def friendGui = 'newgui Friends [\n'
			def cnt = 1
			def mapCnt = 0
			def id = peer.nodeId.toStringFull()
			def mname = "Limbo"
			def myMap = mapTopic ? getMap(mapTopic.getId().toStringFull()) : null
			def mapTab = ''
			def newMapPlayers = []

			updateMapGui()
			mapCnt = 1
			cloudProperties.each('player/(..*)') {key, value, match ->
				def pid = match.group(1)

				if (pid != id) {
					def who = getPlayer(pid)
					def map = (!who.map || who.map == 'none') ? 'none' : getMap(who.map)?.name ?: 'unknown map'

					friendGui += "guibutton [$who.name ($map)] [alias tc_whisper $who.id; alias selected_friend [$who.name]; alias mapIsPrivate $mapIsPrivate; showgui Friend]\n"
					++cnt
					if (myMap?.id == who.map) {
						mapTab += "guibutton [$who.name] [echo $who.id]\n"
						newMapPlayers.add(who)
						++mapCnt
					}
				}
			}
			newMapPlayers.sort {a, b -> a.name.compareTo(b.name)}
			if (newMapPlayers != mapPlayers) {
				mapPlayers = newMapPlayers
				swing.doLater {
					mapPlayersCombo.removeAllItems()
					mapPlayersCombo.addItem('')
					for (player in mapPlayers) {
						mapPlayersCombo.addItem(player.name)
					}
				}
			}
			if (cnt == 1) friendGui += 'guitext "Sorry, no friends are online!"\n'
			friendGui += "guibar\n guibutton Close [cleargui]\n"
			if (myMap) {
				friendGui += "guitab $myMap.name\n$mapTab\n"
				if (mapCnt == 1) friendGui += "guitext [Sorry, no friends are connected to $myMap.name!]\n"
				println "MAPCNT: $mapCnt"
				friendGui += "guibar\n guibutton Close [cleargui]\n"
			}
			friendGui += "]; peers $cnt; tc_mapcount $mapCnt; tc_loadmsg ${myMap ? myMap.name : 'none'}"
			sauer('friend', cvtNewlines(friendGui))
			dumpCommands()
		}
	}
	def shareMap(id) {
		def key = "privateMap/${mapTopic.getId().toStringFull()}"
		def value = cloudProperties[key]

		peer.sendCmds(Id.build(id), ["setCloudProperty $key $value"] as String[])
	}
	def updateMapGui() {
		def ents = []
		def privates = []

		playerCount = [:]
		cloudProperties.each('map/(.*)') {key, value, match ->
			playerCount[match.group(1)] = 0
		}
		cloudProperties.each('privateMap/(.*)') {key, value, match ->
			playerCount[match.group(1)] = 0
		}
		cloudProperties.each('player/(.*)') {key, value, match ->
			def player = getPlayer(match.group(1))

			if (player.map && (playerCount[player.map] == 0 || playerCount[player.map])) {
				playerCount[player.map]++
			}
		}
		def mapsGui = "newgui Worlds ["
		def newMaps = []
		cloudProperties.each('map/(.*)') {key, value, match ->
			def map = getMap(match.group(1))

			ents.add([map.name, map.id, playerCount[map.id]])
			newMaps.add(map)
		}
		ents.sort {a, b -> a[0].compareTo(b[0])}
		for (world in ents) {
			mapsGui += "guibutton [${world[0]} (${world[2]})] [remotesend connectWorld ${world[1]}]\n"
		}
		newMaps.sort {a, b -> a.name.compareTo(b.name)}
		if (newMaps != maps) {
			maps = newMaps
			swing.doLater {
				def sel = mapCombo.selectedItem

				mapCombo.removeAllItems()
				mapCombo.addItem('')
				for (map in maps) {
					mapCombo.addItem(map.name)
				}
				mapCombo.selectedItem = sel
			}
		}
		cloudProperties.each('privateMap/(.*)') {key, value, match ->
			def map = getMap(match.group(1))

			ents.add([map.name, map.id, playerCount[map.id]])
			privates.add([map.name, map.id, playerCount[map.id]])
		}
		if (privates) {
			privates.sort {a, b -> a[0].compareTo(b[0])}
			mapsGui += "guitab [Private Worlds]\n"
			privates.each {
				mapsGui += "guibutton [${it[0]} (${it[2]})] [remotesend connectWorld ${it[1]}]\n"
			}
		}
		mapsGui += "]\nnewgui Portals ["
		for (world in ents) {
			mapsGui += "guibutton [${world[0]} (${world[2]})] [remotesend createPortal ${world[0]} ${world[1]}]\n"
		}
		mapsGui += "]\n"
		sauer('maps', cvtNewlines(mapsGui))
		dumpCommands()
	}
	def loadCostume(who) {
		if (who.costume) {
println "loading costume: $who.costume"
			def costume = getCostume(who.costume)
			def costumeFile = new File(plexusDir, "models/$costume.dir")

			if (costumeFile.exists()) {
				clothe(who, costume.dir)
			} else {
				fetchDir(costume.dir, new File(plexusDir, "models/$costume.dir"), receiveResult: {r ->
					clothe(who, costume.dir)
				}, receiveException: {ex -> err("Could not fetch data for costume: $costume.dir", ex)})
			}
		}
	}
	def clothe(who, costumeDir) {
println "Clothing $who.id with costume: $costumeDir"
println "SENDING: playerinfo ${ids[who.id]} [] $costumeDir"
		sauer('clothe', "playerinfo ${ids[who.id]} [] $costumeDir")
dumpCommands()
	}
	def pushCostume(name) {
println "PUSHING COSTUME: $name"
		pushCostumeDir(name, new File(plexusDir, "models/$name"))
	}
	def pushCostumeDir(name = path ? (path as File).getName() : null, path) {
		if (!path?.exists()) {
			sauer('err', "tc_msgbox [File costume not found] [Could not find costume in directory $path]")
			dumpCommands()
		} else {
println "STORING COSTUME"
			storeDir(path, receiveResult: {result ->
				def fileId = result.file.getId().toStringFull()
				def type = 'png'
				def thumb = result.properties['thumb.png']

				if (!thumb) {
					type = 'jpg'
					thumb = result.properties['thumb.jpg'] ?: 'none'
				}
				try {
println "STORED COSTUME, adding"
					transmitSetCloudProperty("costume/$fileId", "$thumb ${thumb ? type : 'none'} $name")
				} catch (Exception ex) {
					err("Error pushing costume", ex)
				}
			}, receiveException: {ex -> err("Couldn't store costume in cloud: $path", ex)})
		}
	}
	def updateCostumeGui() {
		def costumesDir = new File(plexusDir, 'models')
		def tumes = []
		def needed = []

		cloudProperties.each('costume/(.*)') {key, value, match ->
			def tume = getCostume(match.group(1))

			tumes.add(tume)
			if (tume.thumb && !new File(costumesDir, "thumbs/${tume.dir}.$tume.type").exists()) {
				needed.add(tume)
			}
		}
		println "Tumes: $tumes, Needed: $needed"
		if (needed) {
			serialContinuations(needed, receiveResult: {files ->
				def i = 0
	
				for (i = 0; i < needed.size(); i++) {
					if (files[i] instanceof Exception) {
						System.err.println "Error fetching thumb for costume: ${needed[i].name}..."
						files[i].printStackTrace()
					} else {
						def thumbFile = new File(costumesDir, "thumbs/${needed[i].dir}.${needed[i].type}")

						thumbFile.getParentFile().mkdirs()
						copyFile(files[i][0], thumbFile)
					}
				}
				showTumes(tumes)
			}, receiveException: {ex -> err("Error fetching thumbs for costumes", ex)}) {tume, chain ->
				fetchFile(chain, Id.build(tume.thumb))
			}
		} else {
			showTumes(tumes)
		}
	}
	def showTumes(tumes) {
		println "CREATING EMPTY"
		def trips = []

		tumes.sort {a,b -> a.name.compareTo(b.name)}
		if (tumes != this.tumes) {
			this.tumes = tumes
			swing.doLater {
				tumesCombo.removeAllItems()
				tumesCombo.addItem('')
				tumes.each {
					tumesCombo.addItem(it.name)
				}
			}
		}
		tumes.each {c-> trips.add([c.name, c.thumb ? "${c.dir}.$c.type" : '', c.dir])}
		dumpCostumeSelections(trips)
	}
	//varval = [do [result $@arg1]]
	//x3 = hello; v = 3; echo (varval (concatword x $v))
	/**
	 * name, thumb path, costume id
	 */
	def dumpCostumeSelections(triples) {
		def guitext = ''
		def i = 0

println "COSTUME SELS: $triples"
		guitext += 'showcostumesgui = [showgui Costumes];'
		guitext += 'alias showcostume [ guibar; guiimage (concatword "packages/plexus/models/thumbs/" (get $costumenames [[@@guirollovername]])) $guirolloveraction 4 1 "packages/plexus/dist/tc_logo.jpg"];'
		guitext += "alias costumenames ["
		for (trip in triples) {
//			guitext += " [$i: ${trip[0]}] [${trip[1] ?: ''}]"
			guitext += " [${trip[0]}] ${trip[1]}"
		}
		guitext += " ];"
		guitext += 'newgui Costumes [ \n guilist [ \n   guilist [ \n'
		i = 0
		for (trip in triples) {
//			guitext += "guibutton [$i: ${trip[0]}] [remotesend useCostume ${trip[0]} ${trip[2]}];"
			guitext += "guibutton [${trip[0]}] [remotesend useCostume ${trip[0]} ${trip[2]}];"
		}
		guitext += '] \n   showcostume  \n     ] \n guitab "Upload"; guifield costume_push_name [] \n guibutton [Import Costume] [remotesend pushCostume $costume_push_name]] '
		sauer('gui', cvtNewlines(guitext))
		dumpCommands()
	}
	def useCostume(name, dirId) {
		if (costume != dirId) {
			def costumeDir = new File(plexusDir, "models/$dirId")

			fetchDir(dirId, costumeDir, receiveResult: {
				costume = dirId
				updateMyPlayerInfo()
				sauer('cost', "playerinfo p0 [${LaunchPlexus.props.guild}] ${costumeDir.getName()}")
				dumpCommands()
				println "USE COSTUME $name ($costumeDir)"
				selectCostume()
			}, receiveException: {ex -> err("Couldn't use costume: $name", ex)})
		}
	}
	def selectCostume() {
		tumesCombo.selectedItem = costume ? getCostume(costume)?.name ?: '' : ''
	}
	def connectWorld(id) {
		if (id) {
			def map = getMap(id)
			
			if (!map) {
				sauer('entry', "tc_msgbox [Couldn't find map] [Unknown map id: $id]")
			} else if (map.id != mapTopic?.getId()?.toStringFull()) {
				println "CONNECTING TO WORLD: $map.name ($map.id)"
				if (mapTopic) {
					peer.unsubscribe(mapTopic)
				}
				loadMap(map.name, map.dir, continuation(receiveResult: {
					peer.subscribe(Id.build(id), continuation(receiveResult: {topic ->
						exec {
							mapTopic = topic
							mapIsPrivate = map.privateMap
							updateMyPlayerInfo()
							selectMap()
						}
					},
					receiveException: {exception -> exec {err("Couldn't subscribe to topic: ", exception)}}))
				},
				receiveException: {ex -> exec {err("Trouble loading map", ex)}}))
			}
		} else {
			if (mapTopic) {
				peer.unsubscribe(mapTopic)
				mapTopic = null
				sauer('limbo', "map plexus/dist/limbo/map")
				dumpCommands()
				updateMyPlayerInfo()
			}
		}
	}
	def pushMap(privateMap, String... nameArgs) {
println "pushMap: [$nameArgs]"
		def name = nameArgs.join(' ')
		def newMap = name?.length()

		if (newMap || mapTopic) {
			def map = mapTopic ? getMap(mapTopic.getId().toStringFull()) : null

			if (!newMap) {
				name = map.name
			}
			println "1"
			def cont = continuation(receiveResult: {result ->
				def topic = newMap ? peer.randomId().toStringFull() : map.id
				def id = result.file.getId().toStringFull()

				transmitSetCloudProperty("${privateMap == '1' ? 'privateMap' : 'map'}/$topic", "$id $name")
			},
			receiveException: {ex -> exec {err("Error pushing map", ex)}})

			if (mapname ==~ 'plexus/.*/map') {
				println "plexus"
				def mapdir = new File(sauerDir, "packages/$mapname").getParentFile()

				println "store"
				storeDir(cont, mapdir)
			} else {
				println "sauer"
				def prefix = (new File(mapname).getParent() ? new File(sauerDir, "packages/$mapname") : new File(sauerDir, "packages/base/$mapname")).getAbsolutePath()
				def dirmap = ['map.ogz': new File(prefix + ".ogz")]
				def thumbJpg = new File(prefix + ".jpg")
				def thumbPng = new File(prefix + ".png")
				def cfg = new File(prefix + ".cfg")

				if (thumbJpg.exists()) {
					dirmap['map.jpg'] = thumbJpg
				}
				if (thumbPng.exists()) {
					dirmap['map.png'] = thumbPng
				}
				if (cfg.exists()) {
					dirmap['map.cfg'] = cfg
				}
				println "store"
				storeDir(cont, dirmap)
			}
		} else {
			sauer('msg', "tc_msgbox [Error] [No current map]")
		}
	}
	def activePortals(ids) {
		ids = ids as Set
		portals.keySet().retainAll(ids)
		for (id in ids) {
			if (!portals.containsKey(id)) {
				portals[id] = ""
			}
		}
	}
	def bindPortal(id, topic) {
		portals[id] = topic
		if (cloudProperties["map/$topic"]) {
println "bindPortal: portal_$id = ${getMapName(getMapEntry(topic))}"
			sauer('portal', "portal_$id = ${getMapName(getMapEntry(topic))}")
			dumpCommands()
		}
	}
	def firePortal(id) {
println "portals: $portals, id: $id, portals[id]: ${portals[id]}"
		if (portals[id]) {
			connectWorld(portals[id])
		}
	}
	def createPortal(name, id) {
		def triggers = [] as Set

		for (def i = 0; i < portals.size(); i++) {
			triggers.add(31000 + i)
		}
		triggers.removeAll(portals.keySet())
		def trigger = triggers.isEmpty() ? 31000 + portals.size() : triggers.iterator().next()
		portals[trigger as String] = id
println "createPortal portal_$trigger = $name; portal $trigger"
		sauer('portal', "portal_$trigger = $name; portal $trigger")
		dumpCommands()
		saveGroovyData()
	}
	def saveGroovyData() {
		def cfg = new File(mapname ==~ '.*/.*' ? new File(sauerDir, "packages") : new File(sauerDir, "packages/base"), mapname + ".cfg")
		def txt = ""

		if (cfg.exists()) {
			txt = cfg.getText()
			def pos = txt =~ '(' + Prep.MARKER + '\n)[^\n]*(\n|$)'

			if (pos.find()) {
				txt = txt.substring(0, pos.start(1)) + txt.substring(pos.end(2))
			}
		}
		if (portals) {
			txt += Prep.MARKER
			txt += "remotesend bindPortals ["
			for (portal in portals) {
				txt += "$portal.key $portal.value"
			}
			txt += "];findPortals"
		}
		cfg.write(txt)
	}
	def playerUpdate(id, update) {
		if (id == followingPlayer?.id) {
			def values = [:]
			def format = []

			for (def i = 0; i < update.length; i += 2) {
				values[update[i]] = update[i + 1]
			}
			values.x = (Double.parseDouble(values.x) - 20) as String
			values.y = (Double.parseDouble(values.y) - 20) as String
			values.each {
				format.add(it.key)
				format.add(it.value)
			}
			sauer('follow', "tc_setinfo p0 ${format.join(' ')}")
			dumpCommands()
			broadcast(["update $name ${format.join(' ')}"])
		}
	}
	def clearUploadProgress() {
		if (swing) {
			swing.edt {
				downloadProgressBar.setValue(0)
			}
		}
		exec {
			sauer('up', 'tc_piechart_image = ""')
			dumpCommands()
		}
	}
	def showUploadProgress(cur, total) {
		if (swing) {
			swing.edt {
				downloadProgressBar.setMaximum(total)
				downloadProgressBar.setValue(cur)
			}
		}
		exec {
			def x = total > 0 ? Math.round(cur/total*16.0) : 0
			sauer('up', 'tc_piechart_image = "packages/plexus/dist/ul_' + x + '.png"')
			dumpCommands()
		}
	}
	def clearDownloadProgress() {
		if (swing) {
			swing.edt {
				downloadProgressBar.setValue(0)
			}
		}
		exec {
			sauer('up', 'tc_piechart_image = ""')
			dumpCommands()
		}
	}
	def showDownloadProgress(cur, total) {
		if (swing) {
			swing.edt {
				downloadProgressBar.setMaximum(total)
				downloadProgressBar.setValue(cur)
			}
		}
		exec {
			def x = total > 0 ? Math.round(cur/total*16.0) : 0
			sauer('up', 'tc_piechart_image = "packages/plexus/dist/dl_' + x + '.png"')
			dumpCommands()
		}
	}
}
