import p2pmud.Tools
import java.util.jar.*

public class BuildPlexusJar {
	
	static peer = ""
	static libs = ""
	static plx = ""
	static sauer = ""
	static build = ""
	static version = ""	
	
	static void main(String[] args) {
		peer = Tools.getCwd()
		plx = new File(peer, "../../Plexus")
		sauer = new File(peer, "../SauerbratenRemote")
		build = new File("/tmp/plexus-build")
		version = new File(build, "dist/version")
		
		// if called with -clean, nuke the build folder first
		if (args.length > 0 && args[0] == "-clean") {
			Tools.deleteAll(build)
		}
		
		Tools.deleteAll(new File("/tmp/plexus.jar"))
		
		new File(build, "dist").mkdirs()
		def libDir = new File(peer, "lib") 
		libDir.list( [accept:{d, f-> f ==~ /.*\.jar/ }] as FilenameFilter ).toList().each( { it ->
			def fn = it.toString()
			if (fn != "bouncycastle.jar") {
				fn = new File(libDir, fn)
				println "Checking $fn to extract"
				if (!version.exists() || fn.lastModified() > version.lastModified()) {
					extractZip(fn.toString(), build)
				}
			}
		} )
		
		// copy over remaining files
		Tools.copyAll(new File(peer, "bin"), build)
		Tools.copyAll(new File(plx, "bin"), build)
		Tools.copyAll(new File(sauer, "src/sauer_client"), new File(build, "dist/sauerbraten_plexus_linux"))

		// update the timestamp on the version file
		def today = new Date();
		if (version.exists()) version.delete()
		version << "PLEXUS BUILD $today"
		
		createManifest()
		
		// new JarFile("/tmp/plexus.jar")
//		cd $build
//		find dist -type f >> dist/manifest
//		cd $build
//		zip -9rDXq /tmp/plexus.jar *
		
		
		println ""
		println "plexus.jar build complete"
	}

	static void createManifest() {
		def mani = new File(build, "dist/manifest")
		if (mani.exists()) mani.delete()
		def dist = new File(build, "dist")
		dist.eachFile( { it -> if (it.isFile()) mani << it.name << '\n' } )
	}
	
	static void extractZip(zipfile, destDir) {
		def jar = new JarFile(zipfile)
		//println "Jar: $jar"
		def entries = jar.entries()
		//println entries
		entries.each( { entry ->
			def newFn = new File(destDir, entry.getName())
	        if(entry.isDirectory()) {
	          // Assume directories are stored parents first then children.
	          System.err.println("Extracting directory: " + newFn);
	          // This is not robust, just for demonstration purposes.
	          newFn.mkdirs();
	        } else {
		        System.err.println("Extracting file: " + newFn);
		        copyInputStream(jar.getInputStream(entry),
		           new BufferedOutputStream(new FileOutputStream(newFn)));
	        }
		} )
	}
	
	static void copyInputStream(input, out) {
	    byte[] buffer = new byte[4096];
	    int len;

	    while((len = input.read(buffer)) >= 0)
	      out.write(buffer, 0, len);

	    input.close();
	    out.close();
	  }

}