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
		auto_sauer: '1',
		node_interface: '',
		past_storage:'/tmp/storage-9090'
	] as Properties
	def static props = new Props()
	def static fields = [:]
	def static itemsCombo
	def static nodeIdLabel
	def static modeGroup
	def static modeButtons = [:]
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

				file.getParentFile().mkdirs()
				Tools.copyStreamToFile(input, file)
				input.close()
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
		props[key] = fields[key].text
	}
	def static showprop(key) {
		 fields[key].text = props[key]
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
		    System.out.println( "Found device " + testIGD.getIGDRootDevice().getModelName() );
		    fields['external_ip'].text = testIGD.getExternalIPAddress()
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
		   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
		} catch (Exception e) {}
		new SwingBuilder().build {
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: p[key], constraints: 'span 2, wrap, growx')
			}
			f = frame(title: 'Plexus Configuration', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				field('Your name: ', 'name')
				field('Team/Guild: ', 'guild')
				//field('External IP: ', 'external_ip')
				label(text: 'External IP: ')
				fields['external_ip'] = textField(actionPerformed: {setprop('external_ip')}, focusLost: {setprop('external_ip')}, text: p['external_ip'], constraints: 'growx')
				button(text: "Discover", actionPerformed: { discoverExternalIP() }, constraints: 'wrap')
				field('External port: ', 'external_port')
				field('Use UPnP: ', 'upnp')
				field('Pastry port: ', 'pastry_port')
				field('Pastry boot host: ', 'pastry_boot_host')
				field('Pastry boot port: ', 'pastry_boot_port')
				field('Sauer cmd: ', 'sauer_cmd')
				field('Sauer port: ', 'sauer_port')
				label(text: 'Launch sauer: ')
				modeGroup = buttonGroup();
				panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
		        	modeButtons.launch = radioButton(text:"Launch", buttonGroup:modeGroup, actionPerformed: {setMode(it.source)})
		        	modeButtons.noLaunch = radioButton(text:"No Launch", buttonGroup:modeGroup, actionPerformed: {setMode(it.source)})
				}
				label(text: "Node id: ")
				nodeIdLabel = label(text: props.nodeId ?: "none", constraints: 'wrap, growx')
				button(text: "Start", actionPerformed: {f.dispose(); finished(true)})
				button(text: "Exit", actionPerformed: {f.dispose(); finished(false)}, constraints: 'wrap')
				panel(layout: new MigLayout('fillx,ins 0'), constraints: 'wrap, spanx') {
					itemsCombo = comboBox(editable: true, actionPerformed: {if (itemsCombo) addProfile(itemsCombo?.editor?.item)})
					removeProfileButton = button(text: 'Remove Profile', actionPerformed: {removeProfile()}, enabled: false)
					button(text: 'Clear P2P Cache', actionPerformed: { clearCache() } )
				}
			}
			update()
			f.size = [500, (int)f.size.height] as Dimension
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
	    	if (f.isDirectory()) f.delete()
		}
	}
	def static setMode(button) {
		modeButtons.each {
			if (it.value == button) {
				props.sauer_mode = it.key
			}
		}
	}
	def static update() {
		itemsCombo.model = new DefaultComboBoxModel(['', *props.profiles.sort()] as Object[])
		modeGroup.setSelected(modeButtons[props.sauer_mode ?: 'launch'].model, true)
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
	def static profileSelected(evt) {
		Thread.start {
			def d
			def name
			def ok = {
				d.visible = false
				addProfile(name.text)
			}

			switch (evt.source.selectedIndex) {
			case 0:
				println "new profile"
				new SwingBuilder().build {
					d = dialog(modal: true, layout: new MigLayout('fillx'), pack: true) {
						label(text: 'Profile Name: ')
						name = textField(actionPerformed: ok, constraints: 'wrap, growx')
						button(text: 'OK', actionPerformed: ok)
						button(text: 'Cancel', actionPerformed: {d.visible = false})
					}
					d.visible = true
				}
				evt.source.selectedIndex = -1
				break
			case -1:
				println "no selection"
				break
			default:
				chooseProfile(evt.source.selectedItem)
				break
			}
		}
	}
	def static chooseProfile(item) {
		props.setProfile(item)
		removeProfileButton.enabled = !!props.profile
		fields.each {
			showprop(it.key)
		}
		nodeIdLabel.text = props.nodeId
		update()
		itemsCombo.selectedItem = item
	}
	def static removeProfile() {
		props.removeProfile()
		chooseProfile('')
	}
}
