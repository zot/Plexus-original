import javax.swing.text.html.HTMLEditorKit
import javolution.util.FastTable
import p2pmud.NoWrapEditorKit
import org.stringtree.json.JSONWriter
import org.stringtree.json.JSONReader
import javax.swing.border.BevelBorder
import org.jdesktop.swingx.JXStatusBar
import javax.swing.plaf.FontUIResource
import org.jdesktop.swingx.painter.GlossPainter
import java.awt.Color
import org.jdesktop.swingx.border.DropShadowBorder
import groovy.swing.SwingXBuilder
import java.awt.Font
import p2pmud.Tools
import static p2pmud.Tools.*
import java.awt.event.ItemEvent
import java.util.concurrent.Executors
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
	def gui
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
	def sandbox
	def peer
	def mapname
	def costume
	def mapTopic
	def mapProps = [:]
	def newMapHookBlock
	def globalProps = [:]
	def mapIsPrivate
	/** cloudProperties is the shared properties object for PLEXUS
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
	def mappingFields = [:]

	def static sauerExec
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
	def static err(msg, err) {
		println(msg)
		stackTrace(err)
		println "UNSANITIZED STACK TRACE FOLLOWS..."
		err.printStackTrace()
		System.exit(1)
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
		executor.submit({
			try {
				block()
			} catch (Exception ex) {
				err("", ex)
			}
		})
	}
	def _main(args) {	
		exec {
			executorThread = Thread.currentThread()
		}
		name = args[1]
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
		plexusDir = new File(sauerDir, "packages/plexus")
		cloudProperties = new CloudProperties(this, new File(plexusDir, "cache/$name/cloud.properties"))
		cloudProperties.persistentPropertyPattern = ~'(map|privateMap|costume)/..*'
		cloudProperties.privatePropertyPattern = ~'(privateMap)/..*'
		cacheDir = new File(plexusDir, "cache/$name/files")
		mapDir = new File(plexusDir, "cache/$name/maps")
		mapDir.mkdirs()
		def pastStor = new File(plexusDir, "cache/$name/PAST")
		if (LaunchPlexus.props.cleanStart) {
			deleteAll(pastStor)
		}
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
				updatePlayerList()
				updateMapGui()
				updateCostumeGui()
				def props = cloudProperties.properties
				def data = []

				new ArrayList(props.keySet()).sort().each {
					data << ["<b>$it</b>", props[it]]
				}
				showData(cloudFields.properties, "CURRENT CLOUD PROPERTIES: ${new Date()}", 2, data)
			}
			if ((LaunchPlexus.props.sauer_mode ?: 'launch') == 'launch') launchSauer();
			//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());
			try {
//			   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
			   UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
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
				fetchAndSave("costume", key, costume.id, "costumes/$costume.id")
			}
		}
		P2PMudPeer.verboseLogging = LaunchPlexus.props.verbose_log == '1'
		P2PMudPeer.logFile = new File(plexusDir, "cache/$name/plexus.log")
		if (P2PMudPeer.verboseLogging) {
			P2PMudPeer.sauerLogFile = new File(plexusDir, "cache/$name/sauer.log")
			if (P2PMudPeer.sauerLogFile.exists()) P2PMudPeer.sauerLogFile.delete()
		}
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
		ids = [(peerId): 'p0']
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
		updateMappings()
	}
	def showData(field, header, cols, data) {
		def buf = ("" << "<html><body><table><tr colspan=\"$cols\" style=\"background-color: rgb(192,192,192)\"><td><div>$header</div></td></tr>\n")
		def count = 0
		def pane = field.parent.parent

		data.each {row ->
			buf << "<tr${count & 1 ? '' : ' style="background-color: rgb(192,255,192)"'}>"
			row.each {col -> buf << "<td><div>$col</div></td>"}
			buf << "</tr>\n"
			count++
		}
		buf << "</table></body></html>"
		println "buf: $buf"
		swing.edt {
			def horiz = pane.horizontalScrollBar.value
			def vert = pane.verticalScrollBar.value

			field.text = buf.toString()
			swing.doLater {
				pane.horizontalScrollBar.value = horiz
				pane.verticalScrollBar.value = vert
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
		gui = swing.frame(title: "PLEXUS [${LaunchPlexus.props.name}]", size: [500, 500], windowClosing: {System.exit(0)}, pack: true, show: true) {
			def makeTitlePainter = {label, pos = null ->
				compoundPainter() {
		            mattePainter(fillPaint: new Color(0x28, 0x26, 0x19))
	            	textPainter(text: label, font: new FontUIResource("SansSerif", Font.BOLD, 12), fillPaint: new Color(0xFF, 0x99, 0x00))
	            	glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position: pos ?: GlossPainter.GlossPosition.TOP)
				}
	        }
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {sauerEnt(key)}, focusLost: {sauerEnt(key)}, constraints: 'wrap, growx')
			}
			titledPanel(title: ' ', titlePainter: makeTitlePainter("PLEXUS [${LaunchPlexus.props.name}]: Killer App of the Future - Here Today!"), border: new DropShadowBorder(Color.BLACK, 15)) {
				panel(layout: new MigLayout('fill, ins 0')) {
					panel(layout: new MigLayout(), constraints: 'spanx, wrap') {
						label(text: "Node id: ")
						label(text: LaunchPlexus.props.nodeId ?: "none", constraints: 'wrap, growx')
						label(text: "Neighbors: ")
						panel(layout: new MigLayout('fill, ins 0'), constraints: 'spanx,wrap,growx') {
							button(text: "Update Neighbor List", actionPerformed: {updateNeighborList()})
							neighborField = label(text: 'none', constraints: 'wrap, growx')
						}
						label(text: "Command: ")
						fields.cmd = textField(actionPerformed: {cmd()}, constraints: 'wrap, growx')
					}
					tabbedPane(constraints: 'spanx,grow,wrap') {
						scrollPane(name: 'Commands', border: null) {
							box() {
								panel(layout: new MigLayout('fillx')) {
									label(text: 'Generation')
									panel(layout: new MigLayout('fill, ins 0'), constraints: 'growx,wrap') {
										button(text: "Launch Sauerbraten", actionPerformed: {launchSauer()})
										button(text: "Generate Dungeon", actionPerformed: {generateDungeon()})
										button(text: "Load DF Map", actionPerformed: {loadDFMap()})
										panel(constraints: 'growx,wrap')
									}
									label(text: "Current Map: ")
									mapCombo = comboBox(editable: false, actionPerformed: {
										exec {
											if (mapCombo && mapCombo.selectedIndex > -1) {
												connectWorld(mapCombo.selectedIndex == 0 ? null : maps[mapCombo.selectedIndex - 1].id)
											}
										}
									}, constraints: 'wrap')
									label(text: 'Choose Costume')
									tumesCombo = comboBox(editable: false, actionPerformed: {
										exec {
											if (tumesCombo && tumesCombo.selectedIndex > -1) {
												if (tumesCombo.selectedIndex) {
													def tume = tumes[tumesCombo.selectedIndex - 1]
			
													useCostume(tume.name, tume.id)
												}
											}
										}
									}, constraints: 'wrap')
									label(text: "Follow player: ")
									mapPlayersCombo = comboBox(editable: false, actionPerformed: {
										if (mapPlayersCombo && mapPlayersCombo.selectedIndex > -1) {
											followingPlayer = mapPlayersCombo.selectedIndex == 0 ? null : mapPlayers[mapPlayersCombo.selectedIndex - 1]
			println "NOW FOLLOWING: ${followingPlayer?.name}"
										}
									}, constraints: 'wrap')
									button(text: 'Upload Costume', actionPerformed: {
										exec {
											if (costumeUploadField.text) {
												pushCostumeDir(costumeUploadField.text as File)
											}
										}
									})
									panel(constraints: 'growx,wrap', layout: new MigLayout('fill,ins 0')) {
										costumeUploadField = textField(constraints: 'growx', actionPerformed: {
											exec {pushCostumeDir(costumeUploadField.text as File)}
										})
										button(text: '...', actionPerformed: {
											exec {
												def file = chooseFile("Choose a model to upload", costumeUploadField, "Costumes", "")
			
												if (file) {
													pushCostumeDir(file)
												}
											}
										})
									}
								}
							}
						}
						scrollPane(name: 'Stats', border: null) {
							box() {
								panel(name: 'Stats', layout: new MigLayout('fillx')) {
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
									panel(constraints: 'growy')
								}
							}
						}
						panel(name: 'Cloud', layout: new MigLayout('fill')) {
							label(text: 'Neighbors: ')
							cloudFields.neighbors = label(constraints: 'growx,wrap')
							label(text: 'Cloud Properties', constraints: 'growx,spanx,wrap')
							cloudFields.scrollPane = scrollPane(constraints: 'grow,span,wrap', border: null) {
								cloudFields.properties = textPane(editable: false, editorKit: new NoWrapEditorKit())
							}
						}
						panel(name: 'ID Mapping', layout: new MigLayout('fill')) {
							scrollPane(constraints: 'grow,span,wrap', border: null) {
								mappingFields.ids = textPane(editable: false, editorKit: new NoWrapEditorKit())
							}
							scrollPane(constraints: 'grow,span,wrap', border: null) {
								mappingFields.names = textPane(editable: false, editorKit: new NoWrapEditorKit())
							}
						}
					}
					label(minimumSize: [24,24], text: ' ', foregroundPainter: makeTitlePainter('Copyright (C) 2008, TEAM CTHULHU', GlossPainter.GlossPosition.BOTTOM), constraints: 'growx, spanx, height 24, wrap')
				}
			}
			statusBar(border: new BevelBorder(BevelBorder.LOWERED)) {
				downloadPanel = panel(layout: new MigLayout('ins 0 2 0 2,fillx'), constraints: new JXStatusBar.Constraint(JXStatusBar.Constraint.ResizeBehavior.FILL)) {
					label(text: 'Uploads: ')
					uploadCountField = label(text: '0')
					label(text: ' Downloads: ')
					downloadCountField = label(text: '0')
					label(text: ' Current ')
					loadTypeField = label(text: 'Upload')
					label(text: ': ')
					downloadProgressBar = progressBar(constraints: 'growx', minimum: 0, maximum: 100)
				}
			}
		}
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
			sauerExec += " -x\"alias sauerPort $LaunchPlexus.props.sauer_port;alias sauerHost 127.0.0.1\""
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
					swing.edt {
						gui.visible = true
						gui.extendedState = Frame.NORMAL
						gui.requestFocus()
					}
					println "Disconnect"
				};
			}
		}
	}
	def sauer(key, value) {
		checkExec()
		pendingCommands[key] = value
	}
	def hasSauerConnection() {
		socket?.isConnected()
	}
	def dumpCommands() {
		checkExec()
		if (!pendingCommands.isEmpty()) {
			if (socket?.isConnected()) {
				def out = pendingCommands.collect{it.value}.join(";") + '\n'
//				println out
				if (P2PMudPeer?.sauerLogFile) { P2PMudPeer.sauerLogFile << new Date().toString() << ' ' << out;  }
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
			"name [$name]",
			"team [$LaunchPlexus.props.guild]",
			"cleargui 1",
			"showgui Plexus",
			'echo INIT'
		].join(';'))
	 	dumpCommands()
	 	updatePlayerList()
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

			if (cont) {
				if (hasSauerConnection()) {
					newMapHookBlock = {cont.receiveResult(result)}
					println "Retrieved map from PAST: $dir, executing: map [$mapPath/map]"
					sauer('load', "echo loading new map: [$mapPath/map]; tc_loadmsg [$name]; map [$mapPath/map]")
					dumpCommands()
				} else {
					cont.receiveResult(result)
				}
			}
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
	def initBoot() {
		def docFile = new File(plexusDir, "cache/$name/cloud.properties")

		if (docFile.exists()) {
			cloudProperties.load()
		}
		updateMyPlayerInfo()
		if (LaunchPlexus.props.cleanStart) {
			storeCache()
		}
	}
	def initJoin() {
		checkExec()
		deleteAll(cacheDir)
		peer.anycastCmds(plexusTopic, "sendCloudProperties")
	}
	def storeFile(cont, file, mutable = false, cacheOverride = false) {
		def total = P2PMudFile.estimateChunks(file.length())
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
		if (peer) {
			peer.broadcastCmds(plexusTopic, ["removeCloudProperty $key"] as String[])
		}
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
		def map
		def privateMap = false
		
		if (!entry) {
			entry = cloudProperties["map/$id"]
		}
		if (!entry) {
			entry = cloudProperties["privateMap/$id"]
			privateMap = true
		}
		if (entry) {
			map = new JSONReader().read(entry)
			map.id = id
			map.privateMap = privateMap
			/*
			return [
				id: id,
				dir: entry[0],
				name: entry[1..-1].join(' '),
				privateMap: privateMap
			]
			 */
		}
		return map
	}
	def getPlayer(id, entry = null) {
		checkExec()
		def pl

		entry = entry ?: cloudProperties["player/$id"]
		if (entry) {
			pl = new JSONReader().read(entry)
			pl.id = id
			/*
			pl = [
				id: id,
				map: entry[0] == 'none' ? null : entry[0],
				costume: entry[1] == 'none' ? null : entry[1],
				name: entry[2..-1].join(' ')
			]
			*/
		}
		return pl
	}
	def getCostume(id, entry = null) {
		checkExec()
		def costume
		if (!entry) {
			entry = cloudProperties["costume/$id"]
		}
		if (entry) {
			costume = new JSONReader().read(entry)
			costume.id = id
			return costume
			/*
			return [
				id: id,
				thumb: entry[0] == 'none' ? null : entry[0],
				type: entry[1],
				name: entry[2..-1].join(' ')
			]
			*/
		}
		return costume
	}
	def newMapHook(name) {
		println "newmap: $name"
		clearPlayers()
		sauer("delp", "deleteallplayers")
		dumpCommands()
		if (newMapHookBlock) {
			newMapHookBlock()
			newMapHookBlock = null
		}
		mapname = name
		updateMyPlayerInfo()
		def groovyScript = name ==~ '[^/]*' ? new File(sauerDir, "packages/base/${name}.groovy") : new File(sauerDir, "packages/${name}.groovy")
		def runScript = false
		if (groovyScript.exists()) {
			if (mapTopic) {
				def map = getMap(mapTopic.getId().toStringFull())

				runScript = name ==~ ".*/$map.dir/map"
			} else {
				runScript = name ==~ '.*/limbo/map(.ogz)?'
			}
		}
		if (runScript) {
			mapProps = [:]
			sandbox = new Sandbox(this, [groovyScript.parent])
			try {
				sandbox.exec(groovyScript.name)
			} catch (Exception ex) {
				System.err.println "Error while executing map script..."
				stackTrace(ex)
			}
		}
	}
	def updateMyPlayerInfo() {
		if (!headless) {
			def id = mapTopic?.getId()?.toStringFull()
	
			println "UPDATING PLAYER INFO"
	//		after we get the players list, send ourselves out
			def node = peer.nodeId.toStringFull()
	//TODO put tume in here and persist in props
			transmitSetCloudProperty("player/$node", new JSONWriter().write([map: id, costume: costume, name: name, guild: LaunchPlexus.props.guild]))
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
				updateMappings()
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
		sauer('prep', "echo [Welcome player $name to this world.]; createplayer $id $name; playerinfo $id \"$who.guild\"")
		loadCostume(who)
		updateMappings()
	}
	def updateMappings() {
		def data = []

		ids.each {
			data << [it.key, it.value]
		}
		data.sort {a, b -> a[0].compareTo(b[0])}
		showData(mappingFields.ids, "IDs", 2, data)
		data = []
		names.each {
			data << [it.key, it.value]
		}
		data.sort {a, b -> a[0].compareTo(b[0])}
		showData(mappingFields.names, "Names", 2, data)
	}
	def updatePlayerList() {
		if (!peer?.nodeId) return
		synchronized (presenceLock) {
			updateMapGui()

			def id = peer.nodeId.toStringFull()
			def myMap = mapTopic ? getMap(mapTopic.getId().toStringFull()) : [name: 'Limbo', id: '0']
			def allPlayers = [], newMapPlayers = []

			cloudProperties.each('player/(..*)') {key, value, match ->
				def pid = match.group(1)

				if (pid != id) {
					def who = getPlayer(pid)
					allPlayers.add(who)
					if (myMap?.id == who.map) {
						newMapPlayers.add(who)
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
			
			dumpPlayersMenu(myMap, allPlayers)
		}
	}
	def dumpPlayersMenu(myMap, allPlayers) {
		println "DUMPING PLAYERS TO SAUER"
		def PlayerGui = 'alias showpcostume [ guibar; guiimage (concatword "packages/plexus/models/thumbs/" (get $pcostumenames $guirollovername)) $guirolloveraction 4 1 "packages/plexus/dist/tc_logo.jpg"];'
		PlayerGui += "alias pcostumenames ["
		allPlayers.each( {who ->
			def c = getCostume(who.costume)
			def ct = (c != null && c.thumb) ? "${c.id}.$c.type" : ''
			def map = (!who.map || who.map == 'none') ? 'none' : getMap(who.map)?.name ?: 'unknown map'
			PlayerGui += " \"$who.name ($map)\" $ct" 	
		} )
		PlayerGui += " ];"
		
		PlayerGui += 'newgui Players [ \n guilist [ \n   guilist [ \n'
		def i = 0, needClose = true, last = allPlayers.size()
		def wrapAfter = 12
		def cnt = 0
		def mapCnt = 0
		def mapTab = ''
		def mymapid = myMap?.id
				
		allPlayers.each( {who -> 
				def map = (!who.map || who.map == 'none') ? 'none' : getMap(who.map)?.name ?: 'unknown map'

				PlayerGui += "guibutton [$who.name ($map)] [alias tc_whisper $who.id; alias selected_player [$who.name]; alias mapIsPrivate $mapIsPrivate; showgui Player]\n"
				++cnt
				if (++i % wrapAfter == 0) {
					PlayerGui += '] \n showpcostume \n ] \n'
					needClose = false
					if (i != last) {
						def s = Math.min(i, last - 1), e = Math.min(i + wrapAfter, last - 1)
						s = Character.toUpperCase((allPlayers[s].name[0]) as char)
						e = Character.toUpperCase((allPlayers[e].name[0]) as char)
						PlayerGui += " guitab [More $s-$e] \n guilist [ \n   guilist [ \n"; needClose = true
					}
				}
				
				if (mymapid == who.map) {
					mapTab += "guibutton [$who.name] [echo $who.id]\n"
					++mapCnt
				}
		} )
		if (cnt == 0) PlayerGui += 'guitext "Sorry, no players are online!"\n'
		//PlayerGui += "guibar\n guibutton Close [cleargui]\n"
		if (needClose) PlayerGui += '] \n   showpcostume  \n     ] \n'
		
		if (mymapid) {
			PlayerGui += "guitab $myMap.name\n$mapTab\n"
			if (mapCnt == 0) PlayerGui += "guitext [Sorry, no players are connected to $myMap.name!]\n"
			println "MAPCNT: $mapCnt"
			PlayerGui += "guibar\n guibutton Close [cleargui]\n"
		}
		// bump counts to include ourself
		++cnt
		++mapCnt
		PlayerGui += "]; peers $cnt; tc_mapcount $mapCnt; tc_loadmsg ${allPlayers ? myMap.name : 'none'}"
		sauer('Player', cvtNewlines(PlayerGui))
		dumpCommands()
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

		def newMaps = []
		cloudProperties.each('map/(.*)') {key, value, match ->
			def map = getMap(match.group(1))
			def cnt = playerCount[map.id]
			ents.add([map.name + " ($cnt)", map.id, cnt, map.dir])
			newMaps.add(map)
		}
		ents.sort {a, b -> a[0].compareTo(b[0])}
		
		def mapsGui = 'alias showmapthumb [ guibar; guiimage (concatword "packages/plexus/cache/' + name + '/maps/" (get $mapthumbs $guirollovername) "/map.jpg") $guirolloveraction 4 1 "packages/plexus/dist/tc_logo.jpg"];'
		mapsGui += "alias mapthumbs ["
		for (trip in ents) {
			mapsGui += " \"${trip[0]}\" ${trip[3]}"
		}
		mapsGui += " ];"
		mapsGui += "newgui Worlds [ \n guilist [ \n   guilist [ \n"
		def i = 0, needClose = true, last = ents.size()
		def wrapAfter = 12
		for (world in ents) {
			mapsGui += "guibutton [${world[0]}] [remotesend connectWorld ${world[1]}]\n"
			if (++i % wrapAfter == 0) {
				mapsGui += '] \n showmapthumb \n ] \n'
				needClose = false
				if (i != last) {
					def s = Math.min(i, last - 1), e = Math.min(i + wrapAfter, last - 1)
					s = Character.toUpperCase((ents[s][0][0]) as char)
					e = Character.toUpperCase((ents[e][0][0]) as char)
					mapsGui += " guitab [More $s-$e] \n guilist [ \n   guilist [ \n"; needClose = true
				}
			}
		}
		if (needClose) mapsGui += '] \n   showmapthumb  \n     ] \n'
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
			def costumeFile = new File(plexusDir, "models/$who.costume")

			if (costumeFile.exists()) {
				clothe(who, who.costume)
			} else {
				fetchDir(who.costume, new File(plexusDir, "models/$who.costume"), receiveResult: {r ->
					clothe(who, who.costume)
				}, receiveException: {ex -> err("Could not fetch data for costume: $who.costume", ex)})
			}
		}
	}
	def clothe(who, costumeDir) {
println "Clothing $who.id with costume: $costumeDir"
println "SENDING: playerinfo ${ids[who.id]} [${who.guild ?: ''}] $costumeDir"
		sauer('clothe', "playerinfo ${ids[who.id]} [${who.guild ?: ''}] $costumeDir")
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
					thumb = result.properties['thumb.jpg']
				}
				try {
println "STORED COSTUME, adding"
					transmitSetCloudProperty("costume/$fileId", new JSONWriter().write([thumb: thumb, type: thumb ? type : null, name: name]))
				} catch (Exception ex) {
					System.err.println "Error pushing costume..."
					ex.printStackTrace()
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
			if (tume.thumb && !new File(costumesDir, "thumbs/${tume.id}.$tume.type").exists()) {
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
						def thumbFile = new File(costumesDir, "thumbs/${needed[i].id}.${needed[i].type}")

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
		tumes.each {c-> trips.add([c.name, c.thumb ? "${c.id}.$c.type" : '', c.id])}
		dumpCostumeSelections(trips)
	}
	//varval = [do [result $@arg1]]
	//x3 = hello; v = 3; echo (varval (concatword x $v))
	/**
	 * name, thumb path, costume id
	 */
	def dumpCostumeSelections(triples) {
		def guitext = ''

println "COSTUME SELS: $triples"
		guitext += 'showcostumesgui = [showgui Costumes];'
		guitext += 'alias showcostume [ guibar; guiimage (concatword "packages/plexus/models/thumbs/" (get $costumenames [[@@guirollovername]])) $guirolloveraction 4 1 "packages/plexus/dist/tc_logo.jpg"];'
		guitext += "alias costumenames ["
		for (trip in triples) {
			guitext += " [${trip[0]}] ${trip[1]}"
		}
		guitext += " ];"
		guitext += 'newgui Costumes [ \n guilist [ \n   guilist [ \n'
		def i = 0, needClose = true, last = triples.size()
		def wrapAfter = 12
		for (trip in triples) {
			guitext += "guibutton [${trip[0]}] [remotesend useCostume ${trip[0]} ${trip[2]}];"
			if (++i % wrapAfter == 0) {
				guitext += '] \n showcostume \n ] \n'
				needClose = false
				if (i != last) {
					def s = Math.min(i, last - 1), e = Math.min(i + wrapAfter, last - 1)
					s = Character.toUpperCase((triples[s][0]) as char)
					e = Character.toUpperCase((triples[e][0]) as char)
					guitext += " guitab [More $s-$e] \n guilist [ \n   guilist [ \n"; needClose = true
				}
			}
		}
		if (needClose) guitext += '] \n   showcostume  \n     ] \n'
		guitext += 'guitab "Upload"; guifield costume_push_name [] \n guibutton [Import Costume] [remotesend pushCostume $costume_push_name] \n ] '
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
	def clearPlayers() {
		//println "Going to clear players"
		names = [p0: peerId]
		ids = [(peerId): 'p0']
		updateMappings()
	}
	def connectWorld(id) {
		if (id) {
			def map = getMap(id)
			
			if (!map) {
				sauer('entry', "tc_msgbox [Couldn't find map] [Unknown map id: $id]")
			} else if (map.id != mapTopic?.getId()?.toStringFull()) {
				println "CONNECTING TO WORLD: $map.name ($map.id)"
				clearPlayers()
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

				transmitSetCloudProperty("${privateMap == '1' ? 'privateMap' : 'map'}/$topic", new JSONWriter().write([dir: id, name: name]))
			},
			receiveException: {ex ->
				exec {
					System.err.println "Error pushing map..."
					ex.printStackTrace()
				}
			})

			if (mapname ==~ 'plexus/.*/map') {
				println "PLEXUS"
				def mapdir = new File(sauerDir, "packages/$mapname").getParentFile()

				println "store"
				storeDir(cont, mapdir)
			} else {
				println "sauer"
				def prefix = (new File(mapname).parent ? new File(sauerDir, "packages/$mapname") : new File(sauerDir, "packages/base/$mapname")).getAbsolutePath()
				def dirmap = ['map.ogz': new File(prefix + ".ogz")]
				def thumbJpg = new File(prefix + ".jpg")
				def thumbPng = new File(prefix + ".png")
				def cfg = new File(prefix + ".cfg")
				def groovyPfx = "${mapname}."

				new File(prefix).parentFile.eachFileMatch(~"$mapname\\..*groovy") {
					println "FOUND GROOVY FILE: $it"
					dirmap["map.${it.name.substring(groovyPfx.length())}"] = it
				}
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
	def updateDownloads() {
		if (!headless) {
			downloadCountField.text = downloads as String
			uploadCountField.text = uploads as String
		}
	}
	def clearUploadProgress() {
		if (!headless) {
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
	}
	def showUploadProgress(cur, total) {
		if (!headless) {
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
	}
	def clearDownloadProgress() {
		if (!headless) {
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
	}
	def showDownloadProgress(cur, total) {
		if (!headless) {
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
}
