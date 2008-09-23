import p2pmud.PlexusSecurityManager

public class Sandbox {
	private static Object key = PlexusSecurityManager.getKey()
	
	public static void main(String[] args) {
		try {
			PlexusSecurityManager.install()
			PlexusSecurityManager.soleInstance.addSuspiciousThread(Thread.currentThread(), key)
			println readFile('/tmp/duh')
//			println(('/tmp/duh' as File).text)
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	def static readFile(file) {
		def result

		PlexusSecurityManager.runAuthorized(key) {
			result = (file as File).text
		}
		return result
	}
}