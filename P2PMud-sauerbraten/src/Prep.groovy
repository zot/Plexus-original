import javax.swing.ScrollPaneConstantsimport org.jdesktop.swingx.painter.TextPainter
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
	def static finished(start) {
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
		def f

		//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());
		try {
//		   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
		   UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {e.printStackTrace()}
//		UIManager.put("Label.font", new FontUIResource("SansSerif", Font.PLAIN, 12))
		swing = new SwingXBuilder()
		f = swing.frame(title: 'Plexus Configuration', size: [600, 600], location: [200, 300], windowClosing: {System.exit(0)}, pack: true, show: true) {
			def field = {lbl, key, constraints = 'span 2, wrap, growx', useLabel = true ->
				if (useLabel) {
					label(text: lbl)
				}
				def tf = textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: props[key], constraints: constraints)
				fields[key] = [
					setText: {value -> tf.text = value},
					getText: {tf.text}
				]
				tf
			}
			def check = {lbl, key, description ->
				label(text: lbl)
				def cb = checkBox(text: description, actionPerformed: {evt -> props[key] = evt.source.selected ? '1' : '0' }, constraints: 'wrap' )
				fields[key] = [
					setText: {value -> cb.selected = value == '1'},
					getText: {cb.selected ? '1' : '0'}
				]
				cb
			}
			def makeTitlePainter = {label, pos = null ->
				compoundPainter() {
		            mattePainter(fillPaint: new Color(0x28, 0x26, 0x19))
	            	textPainter(text: label, font: new FontUIResource("SansSerif", Font.BOLD, 12), fillPaint: new Color(0xFF, 0x99, 0x00))
	            	glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position: pos ?: GlossPainter.GlossPosition.TOP)
				}
	        }
			titledPanel(title: ' ', titleForeground: Color.WHITE, titlePainter: makeTitlePainter('Properties For PLEXUS: Killer App of the Future - Here Today!'), border: new DropShadowBorder(Color.BLACK, 15)) {
				panel(layout: new MigLayout('fill, ins 0')) {
					panel(layout: new MigLayout('fillx'), constraints: 'wrap,spanx,growx') {
						label(text: 'Active Profile:')
						profilesCombo = comboBox(editable: true, actionPerformed: {if (profilesCombo) addProfile(profilesCombo?.editor?.item)})
						removeProfileButton = button(text: 'Remove Profile', actionPerformed: { if (MessageBox.AreYouSure("Remove Profile", "Are you sure you want to remove the $props.profile profile?")) removeProfile()}, enabled: false)
						panel(constraints: 'growx')
					}
					tabbedPane(constraints: 'grow,wrap') {
						scrollPane(name: 'Settings', verticalScrollBarPolicy: ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, horizontalScrollBarPolicy: ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
							panel(layout: new MigLayout('fillx,ins 0')) {
								panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Player'), constraints: 'wrap,spanx,growx') {
									field('Your name: ', 'name', 'span 2, growx')
									field('Team/Guild: ', 'guild')
								}
								panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Peer'), constraints: 'wrap,spanx,growx') {
									check('Verbose Log', 'verbose_log', 'Turn on verbose logging')
									label(text: "Node id: ")
									nodeIdLabel = label(text: props.nodeId ?: "none", constraints: 'wrap, growx')
									field('Pastry port: ', 'pastry_port')
									check('Override IP autodetect', 'override_autodetect', 'This will prevent PLEXUS from validating your IP address')
									label('External IP: ')
									panel(layout: new MigLayout('fill, ins 0'), constraints: 'wrap, growx, spanx') {
										field('', 'external_ip', 'growx', false)
										button(text: "Discover", toolTipText: 'Use UPnP to discover your external IP. This may not function properly if you are behind multiple firewalls.', actionPerformed: { props.external_ip = testConnectivity().address; showprop('external_ip')})
										field('Port: ', 'external_port', 'width 50px,wrap')
									}
									check('Use UPnP', 'upnp', 'If checked, make sure UPnP is enabled on your router')
								}
								panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Boot Peer'), constraints: 'wrap,spanx,growx') {
									label('Pastry boot host: ')
									panel(layout: new MigLayout('fill, ins 0'), constraints: 'wrap, growx, spanx') {
										field('', 'pastry_boot_host', 'growx', false)
										field('Port: ', 'pastry_boot_port', 'width 50px')
									}
								}
								panel(layout: new MigLayout('fill,ins 0'), border: titledBorder(title: 'Sauerbraten'), constraints: 'wrap,spanx,growx') {
									field('Sauer cmd: ', 'sauer_cmd')
									field('Sauer port: ', 'sauer_port')
									label(text: 'Launch sauer: ')
									sauerButton = checkBox(text: 'If checked, it will auto start the Plexus custom Sauerbraten', actionPerformed: { evt -> props.sauer_mode = evt.source.selected ? 'launch' : 'noLaunch' }, constraints: 'wrap' )
								}
								panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
									button(text: "Start", toolTipText: 'Press to start Plexus', actionPerformed: {f.dispose(); finished(true)})
									button(text: "Save and Exit", toolTipText: 'Save your changes and exit', actionPerformed: {f.dispose(); finished(false)} )
									button(text: "Exit", toolTipText: 'Exit without saving your changes', actionPerformed: { System.exit(0) } )
								}
								button(text: 'Clear P2P Cache', toolTipText: 'Clear the p2p file cache for the current profile', actionPerformed: { clearCache() } )
							}
						}
						panel(name: 'Diagnostics', layout: new MigLayout('fill')) {
							button(text: 'Test connectivity', actionPerformed: {println testConnectivity()}, constraints: 'wrap')
							panel(constraints: 'grow')
						}
					}
					label(minimumSize: [24,24], text: ' ', foregroundPainter: makeTitlePainter('Copyright (C) 2008, TEAM CTHULHU', GlossPainter.GlossPosition.BOTTOM), constraints: 'growx, height 24')
				}
			}
			update()
			chooseProfile(props.last_profile)
		}
	}
	def static testConnectivity(listen = true) {
		if (conProps?.status != 'success') {
			def sock = listen ? new ServerSocket(Integer.parseInt(props.pastry_port)) : null
			def con = new URL("http://plubble.com/p2p.php?port=$props.external_port").openConnection()
			def input = con.getInputStream()

			conProps = [:] as Properties
			conProps.load(input)
			input.close()
			if (sock) {
				sock.close()
			}
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
