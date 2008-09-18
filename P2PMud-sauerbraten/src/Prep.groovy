import javax.swing.plaf.FontUIResource
import java.awt.Font
import org.jdesktop.swingx.painter.GlossPainter
import java.awt.Color
import org.jdesktop.swingx.border.DropShadowBorder
import org.jdesktop.swingx.plaf.nimbus.NimbusLookAndFeelAddons
import groovy.swing.SwingXBuilder
import javax.swing.DefaultComboBoxModel
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel
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
	def static success = false
	def static mainArgs
	def static plexusdir
	def static lock = new Object()
	def static lastPeerName
	def static defaultProps = [
		sauer_port: '12345',
		name: 'bubba-' + System.currentTimeMillis(),
		guild: '',
		pastry_port: '9090',
		pastry_boot_host: '-',
		pastry_boot_port: '9090',
		external_ip: '',
		external_port: '9090',
		headless: '0',
		upnp: '1',
		sauer_mode: 'launch',
		past_storage:'/tmp/storage-9090'
	] as Properties
	def static props = new Props()
	def static fields = [:]
	def static profilesCombo
	def static nodeIdLabel
	def static upnpButton, sauerButton
	def static removeProfileButton
	def static sauerDir
	def static final MARKER = "\n//THIS LINE ADDED BY TEAM CTHULHU'S PLEXUS: PLEASE DO NOT EDIT THIS LINE OR THE NEXT ONE\n"

	def static initProps() {
		for (e in defaultProps) {
			props[e.key] = e.value
		}
		if (System.getProperty('os.name').equalsIgnoreCase('linux')) {
			props.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_linux -t'
		} else {
			props.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_windows.exe -t'
		}
		props.sauer_cmd += " -lplexus/dist/limbo/map.ogz"
	}

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
		initProps()
		def dir = new File('fred').getAbsoluteFile().getParent()
		
		plexusdir = new File(dir, 'packages/plexus')
		props.file = new File(plexusdir, 'plexus.properties')
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
		// if there are any missing props after a read, fill them in with defaults
		for (e in defaultProps) {
			if (!props[e.key]) props[e.key] = e.value
		}
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
		   UIManager.setLookAndFeel("org.jdesktop.swingx.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {e.printStackTrace()}
		UIManager.put("Label.font", new FontUIResource("SansSerif", Font.PLAIN, 12))
		def swing = new SwingXBuilder()
		swing.build {
			def makeTitlePainter = {
				swing.compoundPainter() {
		            swing.mattePainter(fillPaint:Color.BLACK)
		            swing.glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position:GlossPainter.GlossPosition.TOP)
		        }
			}
			def field = {lbl, key, constraints = 'span 2, wrap, growx' ->
				swing.label(text: lbl)
				def tf = swing.textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: p[key], constraints: constraints)
				fields[key] = [
					setText: {value -> tf.text = value},
					getText: {tf.text}
				]
				tf
			}
			def check = {lbl, key, description ->
				swing.label(text: lbl)
				def cb = swing.checkBox(text: description, actionPerformed: {evt -> props[key] = evt.source.selected ? '1' : '0' }, constraints: 'wrap' )
				fields[key] = [
					setText: {value -> cb.selected = value == '1'},
					getText: {cb.selected ? '1' : '0'}
				]
			}
			f = swing.frame(title: 'Plexus Configuration', size: [600, 600], windowClosing: {System.exit(0)}, pack: true, show: true) {
				swing.titledPanel(title: 'Plexus Properties', titlePainter: makeTitlePainter(), border: new DropShadowBorder(Color.BLACK, 15)) {
					swing.panel(layout: new MigLayout('fillx')) {
						swing.label(text: 'Active Profile:')
						swing.panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
							profilesCombo = swing.comboBox(editable: true, actionPerformed: {if (profilesCombo) addProfile(profilesCombo?.editor?.item)})
							removeProfileButton = swing.button(text: 'Remove Profile', actionPerformed: { if (MessageBox.AreYouSure("Remove Profile", "Are you sure you want to remove the $props.profile profile?")) removeProfile()}, enabled: false)
						}
						field('Your name: ', 'name')
						field('Team/Guild: ', 'guild')
						field('External IP: ', 'external_ip', 'growx')
						swing.button(text: "Discover", actionPerformed: { discoverExternalIP() }, constraints: 'wrap')
						field('External port: ', 'external_port')
						check('Use UPnP', 'upnp', 'If checked, make sure UPnP is enabled on your router')
						field('Pastry port: ', 'pastry_port')
						field('Pastry boot host: ', 'pastry_boot_host')
						field('Pastry boot port: ', 'pastry_boot_port')
						field('Sauer cmd: ', 'sauer_cmd')
						field('Sauer port: ', 'sauer_port')
						check('Verbose Log', 'verbose_log', 'Turn on verbose logging')
						swing.label(text: 'Launch sauer: ')
						sauerButton = swing.checkBox(text: 'If checked, it will auto start the Plexus custom Sauerbraten', actionPerformed: { evt -> props.sauer_mode = evt.source.selected ? 'launch' : 'noLaunch' }, constraints: 'wrap' )
						swing.label(text: "Node id: ")
						nodeIdLabel = swing.label(text: props.nodeId ?: "none", constraints: 'wrap, growx')
						swing.panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
							swing.button(text: "Start", actionPerformed: {f.dispose(); finished(true)})
							swing.button(text: "Save and Exit", actionPerformed: {f.dispose(); finished(false)} )
							swing.button(text: "Exit", actionPerformed: { System.exit(0) } )
						}
						swing.button(text: 'Clear P2P Cache', actionPerformed: { clearCache() } )
					}
				}
			}
			update()
			chooseProfile(props.last_profile)
			f.setLocation(200, 200)
//			f.size = [600, (int)f.size.height] as Dimension
		}
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
				initProps()
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
