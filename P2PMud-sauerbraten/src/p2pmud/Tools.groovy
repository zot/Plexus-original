package p2pmud

import org.bouncycastle.crypto.digests.SHA1Digest
import rice.p2p.util.SecurityUtilsimport rice.pastry.Id
import java.util.zip.ZipFilepublic class Tools {	def static digest = new SHA1Digest()

	def static deleteAll(file) {
		def f = file as File
	
		if (f.exists()) {
			if (f.isDirectory()) {
				f.eachFile {child ->
					deleteAll(child)
				}
			}
			f.delete()
		}
	}
	def static copyAll(from, to) {
		from = from as File
		to = to as File
		if (from.isFile()) {
			copyFile(from, to);
		} else {
			if (!to.exists()) {
				to.mkdir();
			}
			from.eachFile {
				copyAll(it, new File(to, it.getName()))
			}
		}
	}
	def static copyFile(from, to) {
		from = from as File
		to = to as File
		if (to.exists() && to.isDirectory()) {
			throw new RuntimeException("Cannot copy file as directory");
		}
		def tostream = to.newOutputStream()
		tostream.write(from.readBytes())
		tostream.close()
	}
	def static copyZipDir(zipfile, from, to) {
		zipfile = new ZipFile(zipfile as File)
		for (i in zipfile.entries()) {
			if (i.getName().startsWith(from)) {
				if (!i.isDirectory()) {
					def tofile = new File(to, i.getName().substring(from.length() + 1))
					println "copy $i to $tofile"
					tofile.getParentFile().mkdirs()
					def fromstream = zipfile.getInputStream(i)
					def tostream = new File("$to/${i.getName().substring(from.length() + 1)}").newOutputStream()
					def bytes = new byte[10240]
					def count

					while ((count = fromstream.read(bytes)) > -1) {
						tostream.write(bytes, 0, count)
					}
					fromstream.close()
					tostream.close()
				}
			}
		}
	}
	def static byte[] decode(str) {
		str.decodeBase64()
	}
	def static String encode(bytes) {
		bytes.encodeBase64()
	}
	def static contentId(byte[] bytes) {
		Id.build(SecurityUtils.hash(bytes))
	}
}