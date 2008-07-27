package p2pmud

import org.bouncycastle.crypto.digests.SHA1Digest
import rice.p2p.util.SecurityUtilsimport rice.pastry.Id
public class Tools {	def static digest = new SHA1Digest()

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