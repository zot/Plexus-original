import p2pmud.Toolsimport javax.swing.JFileChooser

class PrepBuild {
	def static mainArgs

	public static void main(String[] args) {
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
		def testresource = Plexus.getResource('Plexus.class').getFile()
		def basedir
		def jarfile
		def verfile
		def plexusdir = new File(dir, 'plexus')
		def plexusver = new File(plexusdir, 'dist/version')
		if (testresource ==~ /file:.*!.*/) {
			jarfile = new URL(testresource[0..testresource.indexOf('!') - 1]).getFile() as File
		} else {
			basedir = (testresource as File).getParent()
			verfile = new File(basedir, 'build/plexus/dist/version')
		}
		println "comparing $plexusver with ${(verfile ?: jarfile)}"		if (!new File(plexusdir, 'dist').exists() || plexusver.lastModified() < (verfile ?: jarfile).lastModified()) {			println "need new extract"
			Tools.deleteAll(new File(plexusdir, 'dist'))			if (verfile) {				Tools.copyAll(new File(basedir, 'build/plexus/dist'), new File(plexusdir, 'dist'))			} else {				Tools.copyZipDir(jarfile, 'build/plexus/dist', new File(plexusdir, 'dist'))			}
		} else {
			println "no extract needed"
		}
		mainArgs = [1, 2, 3] as String[]
	}
}
