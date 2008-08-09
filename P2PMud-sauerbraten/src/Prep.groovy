import java.awt.Dimension
import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import p2pmud.Tools
import javax.swing.JFileChooser

public class Prep {
	def static success = false
	def static mainArgs
	def static plexusdir
	def static propsFile
	def static lock = new Object()
	def static lastPeerName
	def static defaultProps = [
		sauer_port: '12345',
		name: 'bubba-' + System.currentTimeMillis(),
		pastry_port: '9090',
		pastry_boot_host: '-',
		pastry_boot_port: '9090',
		external_ip: '',
		external_port: '9090',
		headless: '0',
		upnp: '1',
		auto_sauer: '1',
	] as Properties
	def static props = [:] as Properties
	def static fields = [:]
	def static sauerDir

	def static initProps() {
		for (e in defaultProps) {
			props[e.key] = e.value
		}
		if (System.getProperty('os.name').equalsIgnoreCase('linux')) {
			props.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_linux -t'
		} else {
			props.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_windows.exe -t'
		}
		props.sauer_cmd += " -lplexus/dist/limbo/limbo"
	}
	
	def static verifySauerDir(dir) {
		while (!Test.verifySauerdir(dir)) {
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
		propsFile = new File(plexusdir, 'plexus.properties')
		readProps()
		
		if (props.headless != '0') {
			buildMainArgs()
			return
		}
		def str = Plexus.getResourceAsStream('/dist/version')
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
		def testresource = Plexus.getResource('Plexus.class').getFile()
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

		if (!autoexecFile.exists() || (autoexecFile.getText() =~ /ADDED BY TEAM CTHULHU/).size() == 0) {
			autoexecFile.append("\n//THIS LINE ADDED BY TEAM CTHULHU'S PLEXUS: PLEASE DO NOT EDIT THIS LINE OR THE NEXT ONE\nexec packages/plexus/dist/plexus.cfg\n")
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
		if (start) {
			saveProps()
			buildMainArgs()
			Test.sauerExec = props.sauer_cmd
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
		def output = propsFile.newOutputStream()

		props.store(output, "Plexus Properties")
		output.close()
	}
	def static setprop(key) {
		props[key] = fields[key].text
	}
	def static readProps() {
		if (propsFile.exists()) {
			def input = new FileInputStream(propsFile)

			props = [:] as Properties
			props.load(input)
			input.close()
		}
		// if there are any missing props after a read, fill them in with defaults
		for (e in defaultProps) {
			if (!props[e.key]) props[e.key] = e.value
		}
		lastPeerName = props.name
		println props
	}
	def static showPropEditor() {
		def p = props
		def f
		new SwingBuilder().build {
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: p[key], constraints: 'wrap, growx')
			}
			f = frame(title: 'Plexus', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				field('Sauer port: ', 'sauer_port')
				field('Peer name: ', 'name')
				field('Pastry port: ', 'pastry_port')
				field('Pastry boot host: ', 'pastry_boot_host')
				field('Pastry boot port: ', 'pastry_boot_port')
				field('External IP: ', 'external_ip')
				field('External port: ', 'external_port')
				field('Sauer cmd: ', 'sauer_cmd')
				field('Use UPnP: ', 'upnp')
				field('Auto Run Sauer: ', 'auto_sauer')
				label(text: "Node id: ")
				label(text: props.nodeId ?: "none", constraints: 'wrap, growx')
				button(text: "Start", actionPerformed: {f.dispose(); finished(true)})
				button(text: "Exit", actionPerformed: {f.dispose(); finished(false)})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
	}
}
