import java.awt.Dimension
import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import p2pmud.Tools
import javax.swing.JFileChooser

class Prep {
	def static success = false
	def static mainArgs
	def static plexusdir
	def static propsFile
	def static lock = new Object()
	def static defaultProps = [
		sauer_port: '12345',
		name: 'bubba-' + System.currentTimeMillis(),
		pastry_port: '9090',
		pastry_boot_host: '-',
		pastry_boot_port: '9090',
		external_ip: '',
		external_port: '9090',
	] as Properties
	def static props = [:] as Properties
	def static fields = [:]
	def static sauerDir
	public static void main(String[] args) {
		for (e in defaultProps) {
			props[e.key] = e.value
		}
		if (System.getProperty('os.name').equalsIgnoreCase('linux')) {
			props.sauer_cmd = 'plexus/dist/sauerbraten_plexus_linux -f -t'
		} else {
			props.sauer_cmd = 'plexus/dist/sauerbraten_plexus_windows.exe -t'
		}
		def str = Plexus.getResourceAsStream('/build/plexus/version')
		if (str) {
			str.eachLine {
				println it
			}
			str.close()
		} else {
			println "No version file.  Hope that's OK"
		}
		println 'auto start'
		def dir = new File('fred').getAbsoluteFile().getParent()
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
		def testresource = Plexus.getResource('Plexus.class').getFile()
		def basedir
		def jarfile
		def verfile
		plexusdir = new File(dir, 'plexus')
		propsFile = new File(plexusdir, 'plexus.properties')
		def distdir = new File(dir, 'plexus/dist')
		def plexusver = new File(plexusdir, 'dist/version')
		if (testresource ==~ /file:.*!.*/) {
			jarfile = new URL(testresource[0..testresource.indexOf('!') - 1]).getFile() as File
		} else {
			basedir = (testresource as File).getParent()
			verfile = new File(basedir, 'build/plexus/dist/version')
		}
		println "comparing $plexusver with ${verfile ?: jarfile}"
		if (!new File(plexusdir, 'dist').exists() || plexusver.lastModified() < (verfile ?: jarfile).lastModified()) {
			println "need new extract"
			Tools.deleteAll(distdir)
			distdir.mkdirs()
			if (verfile) {
				Tools.copyAll(new File(basedir, 'build/plexus/dist'), distdir)
			} else {
				Tools.copyZipDir(jarfile, 'build/plexus/dist', distdir)
			}
			new File(distdir, 'sauerbraten_plexus_linux').setExecutable(true)
			new File(distdir, 'sauerbraten_plexus_windows.exe').setExecutable(true)
			patchAutoexec()
		}
		synchronized (lock) {
			getProps()
			lock.wait()
		}
	}
	def static patchAutoexec() {
		def autoexecFile = new File(sauerDir, 'autoexec.cfg')

		if (autoexecFile.exists()) {
			if (autoexecFile.getText() ==~ /.*ADDED BY TEAM CTHULHU.*/) {
				return
			}
		}
		autoexecFile.append("//THIS LINE ADDED BY TEAM CTHULHU: DO NOT EDIT THIS LINE OR THE NEXT ONE\n");
		autoexecFile.append("exec plexus/dist/plexus.cfg\n")
	}
	def static finished(start) {
		if (start) {
			def output = propsFile.newOutputStream()

			props.store(output, "Plexus Properties")
			output.close()
			mainArgs = [
				props.sauer_port,
				props.name,
				props.pastry_port,
				props.pastry_boot_host,
				props.pastry_boot_port,
				'-external',
				"$props.external_ip:$props.external_port"
			] as String[]
			Test.sauerExec = props.sauer_cmd
			synchronized (lock) {
				lock.notifyAll()
			}
		} else {
			System.exit(0)
		}
	}
	def static setprop(key) {
		props[key] = fields[key].text
	}
	def static getProps() {
		if (propsFile.exists()) {
			def input = new FileInputStream(propsFile)

			props = [:] as Properties
			props.load(input)
			input.close()
		}
		println props
		def p = props
		def f
		new SwingBuilder().build {
			def field = {lbl, key ->
				label(text: lbl)
				fields[key] = textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: p[key], constraints: 'wrap, growx')
			}
			f = frame(title: 'Position', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				field('Sauer port: ', 'sauer_port')
				field('Peer name: ', 'name')
				field('Pastry port: ', 'pastry_port')
				field('Pastry boot host: ', 'pastry_boot_host')
				field('Pastry boot port: ', 'pastry_boot_port')
				field('External IP: ', 'external_ip')
				field('External port: ', 'external_port')
				field('Sauer cmd: ', 'sauer_cmd')
				button(text: "Start", actionPerformed: {f.dispose(); finished(true)})
				button(text: "Exit", actionPerformed: {f.dispose(); finished(false)})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
	}
}
