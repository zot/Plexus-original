import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import p2pmud.BasicTools
import java.util.jar.*

public class BuildPlexusJar {
	
	static peer = ""
	static libs = ""
	static plx = ""
	static sauer = ""
	static build = ""
	static version = ""	
	
	static void main(String[] args) {
		peer = BasicTools.getCwd()
		plx = new File(peer, "../../Plexus")
		sauer = new File(peer, "../SauerbratenRemote")
		build = new File("/tmp/plexus-build")
		version = new File(build, "dist/version")
		
		// if called with -clean, nuke the build folder first
		if (args.length > 0 && args[0] == "-clean") {
			BasicTools.deleteAll(build)
		}
		
		BasicTools.deleteAll(new File("/tmp/plexus.jar"))
		
		new File(build, "dist").mkdirs()
		def libDir = new File(peer, "lib") 
		libDir.eachFileMatch(~/.*\.jar/) { it ->
			if (it.name != "bouncycastle.jar") {
				println "Checking $it to extract"
				if (!version.exists() || it.lastModified() > version.lastModified()) {
					extractZip(it, build)
				}
			}
		}
		
		// copy over remaining files
		BasicTools.copyAll(new File(peer, "bin"), build)
		BasicTools.copyAll(new File(plx, "build/plexus"), build)
		BasicTools.copyAll(new File(plx, "META-INF"), build)
		BasicTools.copyAll(new File(sauer, "src/sauer_client"), new File(build, "dist/sauerbraten_plexus_linux"))

		// update the timestamp on the version file
		def today = new Date();
		if (version.exists()) version.delete()
		version << "PLEXUS BUILD $today"
		
		createManifest()

		def fileOut = new FileOutputStream('/tmp/plexus.jar')
		def zo = new ZipOutputStream(fileOut)

		build.eachFileRecurse {
			if (!it.isDirectory()) {
				def input = it.newInputStream()

				zo.putNextEntry(new ZipEntry(BasicTools.subpath(build, it)))
				zo << input
				zo.closeEntry()
				input.close()
			}
		}
		zo.finish()
		fileOut.close()
		
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
		dist.eachFileRecurse { it -> if (it.isFile()) mani << BasicTools.subpath(build, it) << '\n' }
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