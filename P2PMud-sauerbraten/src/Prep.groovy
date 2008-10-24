import javax.imageio.ImageIO
import java.awt.BorderLayout
import org.jdesktop.swingx.JXFrame
import javax.swing.JFrame
import com.sun.awt.AWTUtilities
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import p2pmud.PlexusImagePanel
import javax.swing.SwingUtilities
import org.jdesktop.swingx.graphics.GraphicsUtilities
import org.jdesktop.swingx.painter.ImagePainter
import org.jdesktop.swingx.painter.MattePainter
import javax.swing.BoxLayout
import javax.swing.OverlayLayout
import java.awt.CardLayout
import javax.swing.ScrollPaneConstants
import org.jdesktop.swingx.painter.TextPainter
import javax.swing.plaf.FontUIResource
import java.awt.Font
import org.jdesktop.swingx.painter.GlossPainter
import java.awt.Color
import org.jdesktop.swingx.border.DropShadowBorder
import groovy.swing.SwingXBuilder
import javax.swing.DefaultComboBoxModel
import javax.swing.UIManager
import java.awt.Dimension
import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import p2pmud.Tools
import javax.swing.JFileChooser
import net.sbbi.upnp.Discovery.*;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import p2pmud.MessageBox
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseEvent
import java.awt.event.InputEvent

public class Prep {
	def static conProps
	def static success = false
	def static mainArgs
	def static plexusdir
	def static lock = new Object()
	def static lastPeerName
	def static props = new Props()
	def static fields = [:]
	def static profilesCombo
	def static nodeIdLabel
	def static upnpButton, sauerButton
	def static removeProfileButton
	def static sauerDir
	def static final MARKER = "\n//THIS LINE ADDED BY TEAM CTHULHU'S PLEXUS: PLEASE DO NOT EDIT THIS LINE OR THE NEXT ONE\n"
	def static swing
	def static propsWindow
	def static initialClick = null
	def static warningDialog = [:]
	def static successDialog = [:]
	def static testSocket
	def static chosenPort

	def static verifySauerDir(dir) {
		while (!Plexus.verifySauerdir(dir)) {
			def ch = new JFileChooser(dir);
			ch.setDialogTitle("$dir is not a Sauerbraten directory.  Please specify your Sauerbraten directory");
			ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
			if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				dir = ch.getSelectedFile().getName()
			} else {
				println "proceeding as if $dir were a sauer dir"
				break
			}
		}
		System.setProperty('sauerdir', dir as String)
		sauerDir = dir
	}
	public static void main(String[] args) {
		def dir = new File('').getAbsoluteFile()
		plexusdir = new File(dir, 'packages/plexus')
		readProps()
		
		if (props.headless != '0') {
			buildMainArgs()
			return
		}
		def str = LaunchPlexus.getResourceAsStream('/dist/version')
		if (str) {
			str.eachLine {
				println it
			}
			str.close()
		} else {
			println "No version file.  Hope that's OK"
		}
		println 'auto start'
		
		verifySauerDir(dir)
		
		def basedir
		def jarfile
		def verfile
		def testresource = LaunchPlexus.getResource('LaunchPlexus.class').getFile()
		def distdir = new File(plexusdir, 'dist')
		def plexusver = new File(distdir, 'version')
		
		if (testresource ==~ /file:.*!.*/) {
			jarfile = new URL(testresource[0..testresource.indexOf('!') - 1]).getFile() as File
		} else {
			basedir = (testresource as File).getParent()
			verfile = new File(basedir, '/dist/version')
		}
		println "comparing $plexusver with ${verfile ?: jarfile}"
		if (!new File(plexusdir, 'dist').exists() || plexusver.lastModified() < (verfile ?: jarfile).lastModified()) {
			println "need new extract"
			Tools.deleteAll(distdir)
			distdir.mkdirs()
			//use a manifest because windows won't let you read the jar file while it's in use
			def manifest = Prep.getResourceAsStream('/dist/manifest')

			manifest.eachLine {
				def input = Prep.getResourceAsStream('/' + it)
				def file = new File(plexusdir, it)

				if (!input) {
					println "WARNING, NO FILE FOR MANIFEST ENTRY: $it!"
				} else {
					file.getParentFile().mkdirs()
					Tools.copyStreamToFile(input, file)
					input.close()
				}
			}
			manifest.close()
			new File(distdir, 'sauerbraten_plexus_linux').setExecutable(true)
			new File(distdir, 'sauerbraten_plexus_windows.exe').setExecutable(true)
		}
		patchAutoexec()
		synchronized (lock) {
			readProps()
			showPropEditor()
			lock.wait()
		}
	}
	def static patchAutoexec() {
		def autoexecFile = new File(sauerDir, 'autoexec.cfg')

		if (!autoexecFile.exists() || (autoexecFile.getText() =~ MARKER).size() == 0) {
			autoexecFile.append("${MARKER}exec packages/plexus/dist/plexus.cfg\n")
		}
	}
	def static buildMainArgs() {
		choosePort()
		mainArgs = [
					props.sauer_port,
					props.name,
					props.pastry_port,
					props.pastry_boot_host,
					props.pastry_boot_port,
					'-external',
					"$props.external_ip:$props.external_port"
				]
		if (props.nodeId) {
			mainArgs.add('-nodeId')
			mainArgs.add(props.nodeId)
		}
		mainArgs = mainArgs as String[]
	}
	def static choosePort() {
		if (!chosenPort) {
			def startPort = Integer.parseInt(props.pastry_port_start)
			def endPort = Integer.parseInt(props.pastry_port_end)

			chosenPort = (startPort >= endPort)? startPort : (Math.abs(new Random().nextInt()) % (endPort - startPort)) + startPort
			props.pastry_port = chosenPort as String
			props.external_port = chosenPort as String
			println "using port: $props.pastry_port in range: ($startPort - $endPort)"
		}
	}
	def static connectivityReport(showOk = false, reportSuccess = false) {
		def con = testConnectivity(true, false)
		def result = true
		def bad = con.status.toLowerCase() != 'success' || con.address != props.external_ip

		if (bad || LaunchPlexus.privateIp) {
			warningDialog.plubbleIp.text = con.address
			warningDialog.externalPort.text = props.external_port
			warningDialog.okButton.visible = showOk || !bad
			warningDialog.continueButtons.visible = !showOk && bad
			if (con.address != props.external_ip) {
				warningDialog.externalIp.text = props.external_ip
				warningDialog.reassignIpButton.visible = true
				warningDialog.ipBox.visible = true
			} else {
				println "going to remove panel"
				warningDialog.ipBox.visible = false
				warningDialog.reassignIpButton.visible = false
			}
			if (LaunchPlexus.privateIp) {
				warningDialog.routerIp.text = LaunchPlexus.gatewayIp
				warningDialog.privateIp.visible = true
			}
			warningDialog.frame.pack()
			warningDialog.frame.visible = true
			result = warningDialog.continueAnyway
		} else if (reportSuccess) {
			successDialog.frame.visible = true
		}
		if (testSocket) {
			testSocket.close()
		}
		return result
	}
	def static finished(start) {
		if (start && props.pastry_boot_host != '-' && !props.disable_con_check) {
			if (!connectivityReport()) {
				return
			}
		}
		propsWindow.dispose()
		propsWindow = null
		saveProps()
		if (start) {
			buildMainArgs()
			Plexus.sauerExec = props.sauer_cmd
			synchronized (lock) {
				lock.notifyAll()
			}
		} else {
			System.exit(0)
		}
	}
	def static saveProps() {
		//println 'saving props'
		//println props
		props.store()
	}
	def static setprop(key) {
		props[key] = fields[key].getText()
		if (key in ['pastry_port_start', 'pastry_port_end']) {
			chosenPort = null
			conProps = null
		}
	}
	def static showprop(key) {
		 fields[key].setText(props[key])
	}
	def static readProps() {
		props.load()
		lastPeerName = props.name
	}
	def static discoverExternalIP() {
		println "Discover button pressed"
		int discoveryTimeout = 5000; // 5 secs to receive a response from devices
		try {
		  def IGDs = InternetGatewayDevice.getDevices( discoveryTimeout );
		  if ( IGDs != null ) {
		    // let's the the first device found
		    def testIGD = IGDs[0];
		    System.out.println("Found device ${testIGD.getIGDRootDevice().getModelName()}, ip: ${testIGD.getExternalIPAddress()}");
		    fields['external_ip'].setText(testIGD.getExternalIPAddress())
		    setprop('external_ip')
		    
		    return
		  }
		} catch(Exception) {
		  // some IO Exception occured during communication with device
		}
	}
	def static showPropEditor() {
		def p = props
		def topPanel
		//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());
		try {
//		   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
		   UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {e.printStackTrace()}
//		UIManager.put("Label.font", new FontUIResource("SansSerif", Font.PLAIN, 12))
//		def showTitle = {
//			def item = profilesCombo?.editor?.item
//
//			if (propsWindow) {
//				propsWindow.title = "PLEXUS Configuration [${item ? item as String : 'DEFAULT'}]"
//			}
//		}
		swing = new SwingXBuilder()
		def offsetX = 0
		def offsetY = 0
		def field = {lbl, key, constraints = 'span 2, wrap, growx', useLabel = true ->
			if (useLabel) {
				swing.label(text: lbl)
			}
			def tf = swing.textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: props[key], constraints: constraints)
			fields[key] = [
				setText: {value -> tf.text = value},
				getText: {tf.text}
			]
			tf
		}
		def check = {lbl, key, description, constraints = 'growx, wrap' ->
			swing.label(text: lbl)
			def cb = swing.checkBox(text: description, actionPerformed: {evt -> props[key] = evt.source.selected ? '1' : '0' }, constraints: constraints )
			fields[key] = [
				setText: {value -> cb.selected = value == '1'},
				getText: {cb.selected ? '1' : '0'}
			]
			cb
		}
		def makeTitlePainter = {label, pos = null ->
			swing.compoundPainter() {
	            mattePainter(fillPaint: new Color(0x28, 0x26, 0x19))
            	textPainter(text: label, font: new FontUIResource("SansSerif", Font.BOLD, 12), fillPaint: new Color(0xFF, 0x99, 0x00))
            	glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position: pos ?: GlossPainter.GlossPosition.TOP)
			}
        }
		def config;
		if (System.properties['os.name'] != 'Linux') {
			def env = GraphicsEnvironment.getLocalGraphicsEnvironment()
			def devices = env.getScreenDevices();
			for (int i = 0; i < devices.length && config == null; i++) {
				GraphicsConfiguration[] configs = devices[i].getConfigurations();
				
				for (int j = 0; j < configs.length && config == null; j++) {
					if (AWTUtilities.isTranslucencyCapable(configs[j])) {
						config = configs[j];
					}
				}
			}
		}
		propsWindow = config ? new JFrame(config) : new JXFrame()
		swing.widget(propsWindow, iconImage: ImageIO.read(Prep.getResource('/tinyCthulhu.png')), title: 'PLEXUS Configuration', size: [800, 700], location: [200, 300], windowClosing: {println "closing..."; die()}, windowClosed: {println "closed."}, undecorated: true,
			mousePressed: {e ->
				def loc = propsWindow.getLocation()

				offsetX = loc.x - e.getXOnScreen()
				offsetY = loc.y - e.getYOnScreen()
			},
			mouseDragged: {e-> propsWindow.setLocation((int)(e.getXOnScreen() + offsetX), (int)(e.getYOnScreen() + offsetY))}
		)
		propsWindow.contentPane = swing.panel(layout: new BorderLayout()) {
			topPanel = panel(opaque: false, doubleBuffered: false, background: new Color(0, 0, 0, 0), border: new DropShadowBorder(Color.BLACK, 15), layout: new MigLayout('fill, ins 0, gap 0 0')) {
				widget(new PlexusImagePanel(Prep.getResource('/tentacles.png')), doubleBuffered: false, constraints: 'width 48, height 32, pos footer.x2-48 footer.y2-32', background: new Color(255, 255, 255, 0),
					mousePressed: {e ->
    					def sz = propsWindow.size
		
    					offsetX = sz.width - e.getXOnScreen()
    					offsetY = sz.height - e.getYOnScreen()
					},
					mouseDragged: {e-> propsWindow.setSize((int)(e.getXOnScreen() + offsetX), (int)(e.getYOnScreen() + offsetY))}
				)
				def killbox = panel(constraints: 'pos label.x2-32 0, width 32, height 32', doubleBuffered: false, background: new Color(255, 255, 255, 0), backgroundPainter: new ImagePainter(Prep.getResource('/tinyCthulhu.png')), mousePressed: {e -> System.exit(0)})
				killbox.backgroundPainter.scaleToFit = true
				label(minimumSize: [24,24], text: ' ', foregroundPainter: makeTitlePainter('Properties For PLEXUS: Killer App of the Future - Here Today!', GlossPainter.GlossPosition.TOP), constraints: 'id label, width 100%-15, height 24, pos 0 0')
				panel(layout: new BorderLayout(), constraints: 'width 100%-15, height 100%-48-15, pos 0 24') {
					tabbedPane() {
						scroll = scrollPane(name: 'Settings', border: null, verticalScrollBarPolicy: ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, horizontalScrollBarPolicy: ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
							box() {
								panel(layout: new MigLayout('fillx,ins 0,nocache')) {
									panel(layout: new MigLayout(''), constraints: 'wrap,spanx,growx') {
										label(text: 'Active Profile:')
										profilesCombo = comboBox(editable: true, actionPerformed: {
											if (profilesCombo) addProfile(profilesCombo?.editor?.item)
//											showTitle()
										})
										removeProfileButton = button(text: 'Remove Profile', actionPerformed: { if (MessageBox.AreYouSure("Remove Profile", "Are you sure you want to remove the $props.profile profile?")) removeProfile()}, enabled: false)
									}
									panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Player'), constraints: 'wrap,spanx,growx') {
										field('Your name: ', 'name', 'span 2, growx')
										field('Team/Guild: ', 'guild')
									}
									panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Peer'), constraints: 'wrap,spanx,growx') {
										check('Verbose Log', 'verbose_log', 'Turn on verbose logging')
										label(text: "Node id: ")
										nodeIdLabel = label(text: props.nodeId ?: "none", constraints: 'wrap, growx')
										panel(layout: new MigLayout('fill, ins 0'), constraints: 'wrap, spanx') {
											field('Pastry port: ', 'pastry_port_start', 'width 64px')
											label('-')
											field('', 'pastry_port_end', 'width 64px, pushx', false)
										}
										label('External IP: ')
										panel(layout: new MigLayout('fill, ins 0'), constraints: 'wrap, growx, spanx') {
											field('', 'external_ip', 'growx', false)
											button(text: "Discover", toolTipText: 'Discover your external IP.', actionPerformed: { props.external_ip = testConnectivity().address; showprop('external_ip')})
//											field('Port: ', 'external_port', 'width 64px,wrap')
										}
										check('Use UPnP', 'upnp', 'If checked, make sure UPnP is enabled on your router')
									}
									panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Boot Peer'), constraints: 'wrap,spanx,growx') {
										label('Pastry boot host: ')
										panel(layout: new MigLayout('fill, ins 0'), constraints: 'wrap, growx, spanx') {
											field('', 'pastry_boot_host', 'growx', false)
											field('Port: ', 'pastry_boot_port', 'width 64px')
										}
									}
									panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Sauerbraten'), constraints: 'wrap,spanx,growx') {
										field('Sauer cmd: ', 'sauer_cmd')
										field('Sauer port: ', 'sauer_port')
										label(text: 'Launch sauer: ')
										sauerButton = checkBox(text: 'If checked, it will auto start the PLEXUS custom Sauerbraten', actionPerformed: { evt -> props.sauer_mode = evt.source.selected ? 'launch' : 'noLaunch' }, constraints: 'wrap' )
									}
									panel(layout: new MigLayout('fillx'), constraints: 'growx, spanx, wrap') {
										check('Disable connectivity check', 'disable_con_check', 'If checked, PLEXUS will not check your connectivity before starting', '')
										button(text: 'Test Now', actionPerformed: {conProps = chosenPort = null; connectivityReport(true, true)}, constraints: 'pushx')
									}
									panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
										button(text: "Start", toolTipText: 'Press to start PLEXUS', actionPerformed: {finished(true)})
										button(text: "Save and Exit", toolTipText: 'Save your changes and exit', actionPerformed: {finished(false)} )
										button(text: "Exit", toolTipText: 'Exit without saving your changes', actionPerformed: { System.exit(0) } )
									}
									button(text: 'Clear P2P Cache', toolTipText: 'Clear the p2p file cache for the current profile', actionPerformed: { clearCache() } )
								}
							}
						}
					}
				}
				label(minimumSize: [24,24], text: ' ', foregroundPainter: makeTitlePainter('Copyright (C) 2008, TEAM CTHULHU', GlossPainter.GlossPosition.BOTTOM), constraints: 'id footer, height 24, width 100%-15, pos 0 visual.h-24-15')
			}
			update()
			chooseProfile(props.last_profile)
//			showTitle()
			scroll.verticalScrollBar.unitIncrement = 16
		}
		warningDialog.frame = swing.dialog(title: 'Connectivity Warning', modal: true, pack: true) {
			panel(layout: new MigLayout('fill,ins 0, gap 0, hidemode 3')) {
				panel(border: titledBorder(title: 'Connection Data'), constraints: 'growx, spanx, wrap', layout: new MigLayout('fill')) {
					label(text: 'No connectivity from Plubble.com to your machine', font: new FontUIResource("SansSerif", Font.BOLD, 12), constraints: 'growx, spanx, wrap')
					label(text: 'Plubble Reported This External IP: ')
					warningDialog.plubbleIp = label(constraints: 'pushx, wrap')
					label(text: 'Your Profile Specified This External Port: ')
					warningDialog.externalPort = label(constraints: 'pushx, wrap')
				}
				warningDialog.ipBox = panel(border: titledBorder(title: 'Inconsistent External IP'), constraints: 'growx, spanx, wrap', layout: new MigLayout('fill')) {
					label(text: 'Your external address is not consistent with your connection to Plubble', font: new FontUIResource("SansSerif", Font.BOLD, 12), constraints: 'growx, spanx, wrap')
					label(text: 'Profile External IP: ')
					warningDialog.externalIp = label(constraints: 'pushx, wrap')
				}
				warningDialog.privateIp = panel(border: titledBorder(title: 'Router has private IP address'), constraints: 'growx, spanx, wrap', layout: new MigLayout('fill')) {
					label(text: 'Your router appears to be an internal router', font: new FontUIResource("SansSerif", Font.BOLD, 12), constraints: 'growx, spanx, wrap')
					label(text: 'Router\'s IP address: ')
					warningDialog.routerIp = label(constraints: 'pushx, wrap')
				}
				panel(layout: new MigLayout('fillx, ins 0, hidemode 3'), constraints: 'growx,spanx') {
					warningDialog.continueButtons = panel(constraints: 'growx', layout: new MigLayout('fillx, ins 0, hidemode 3')) {
						button(text: "Don't Start Yet...", actionPerformed: {warningDialog.continueAnyway = false; warningDialog.frame.visible = false})
						panel(constraints: 'growx')
						warningDialog.reassignIpButton = panel(constraints: 'growx', visible: false, layout: new MigLayout('fill, ins 0')) {
							button(text: 'Use Detected IP Address, But Don\'t Start Yet...', actionPerformed: {props.external_ip = conProps.address; showProp('external_ip'); warningDialog.continueAnyway = true; warningDialog.frame.visible = false})
							panel(constraints: 'growx')
							button(text: 'Use Detected IP Address And Start...', actionPerformed: {props.external_ip = conProps.address; warningDialog.continueAnyway = true; warningDialog.frame.visible = false})
						}
						panel(constraints: 'growx')
						button(text: 'Start With Current Settings...', actionPerformed: {warningDialog.continueAnyway = true; warningDialog.frame.visible = false})
					}
					warningDialog.okButton = panel(constraints: 'growx', layout: new MigLayout('fillx, ins 0')) {
						panel(constraints: 'growx')
						button(text: "OK", actionPerformed: {warningDialog.continueAnyway = false; warningDialog.frame.visible = false})
						panel(constraints: 'growx')
					}
				}
			}
		}
		successDialog.frame = swing.dialog(title: 'Success!', modal: true, pack: true) {
			panel(layout: new MigLayout('fill,ins 0, gap 0')) {
				panel(border: titledBorder(title: 'Your Settings Appear Correct'), constraints: 'growx, spanx, wrap', layout: new MigLayout('fill')) {
					label(text: 'Plubble has successfuly connected to your machine.', font: new FontUIResource("SansSerif", Font.BOLD, 12), constraints: 'growx, spanx, wrap')
				}
				panel(constraints: 'growx')
				button(text: "OK", actionPerformed: {successDialog.frame.visible = false})
				panel(constraints: 'growx')
			}
		}
		if (config) {
			propsWindow.background = new Color(0, 0, 0, 0)
			com.sun.awt.AWTUtilities.setWindowOpacity(propsWindow, 1)
			com.sun.awt.AWTUtilities.setWindowOpaque(propsWindow, false)
		}
		propsWindow.show()
	}
	def static die() {
		println "DYING, CLEANING UP..."
		LaunchPlexus.cleanAllMappings()
//		LaunchPlexus.cleanMappings()
		System.exit(0)
	}
	def static testConnectivity(listen = true, closeSocket = true) {
		if (conProps?.status != 'success') {
			choosePort()
			println props.pastry_port
			testSocket = listen ? new ServerSocket() : null
			if (testSocket) {
				testSocket.setReuseAddress(true)
				//testSocket.setSoTimeout(10);
				testSocket.bind(new java.net.InetSocketAddress(Integer.parseInt(props.pastry_port)))
			}
			if (props.upnp == '1') LaunchPlexus.pokeHole("Plexus", Integer.parseInt(props.external_port))
			def con = new URL("http://plubble.com/p2p.php?port=$props.external_port").openConnection()
			def input = con.getInputStream()

			conProps = [:] as Properties
			conProps.load(input)
			input.close()
			if (testSocket && closeSocket) {
				testSocket.close()
				testSocket = null
			}
			println "connection test results: $conProps"
		} else {
			println "connection test already succeeded: $conProps"
		}
		return conProps
	}
	def static clearCache() {
		def dir = new File('fred').getAbsoluteFile().getParent()
		plexusdir = new File(dir, 'packages/plexus')
		println "Clearing cache at $plexusdir"

		if (props.name) {
			Tools.deleteAll(new File(plexusdir, "cache/$props.name"))
		}
		Tools.deleteAll(new File(plexusdir, "models/thumbs"))
		
		new File(plexusdir, "models").eachFileMatch(~/^[A-F0-9]+$/){ f->
	    	if (f.isDirectory()) Tools.deleteAll(f)
		}
	}
	def static update() {
		profilesCombo.model = new DefaultComboBoxModel(['', *props.profiles.sort()] as Object[])
		//modeGroup.setSelected(modeButtons[props.sauer_mode ?: 'launch'].model, true)
		sauerButton.setSelected(props.sauer_mode == 'launch')
	}
	def static addProfile(prof) {
		if (prof) {
			if (props.addProfile(prof)) {
				props.setProfile(prof)
				props.initProps()
			}
		}
		chooseProfile(prof)
	}
	def static chooseProfile(item) {
		props.setLastProfile(item)
		println ("setting last_profile to '$props.last_profile'")
		props.setProfile(item)
		removeProfileButton.enabled = !!props.profile
		fields.each {
			showprop(it.key)
		}
		nodeIdLabel.text = props.nodeId
		update()
		profilesCombo.selectedItem = item
	}
	def static removeProfile() {
		props.removeProfile()
		chooseProfile('')
	}
}
