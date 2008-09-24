import p2pmud.PlexusSecurityManager

public class Sandbox {
	private static key = PlexusSecurityManager.getKey()
	private static engine = new GroovyScriptEngine([new File(Sandbox.getResource('Sandbox.class').toURI()).parentFile] as String[])
	private static loader = PlexusClassLoader.create(engine, Sandbox.classLoader, key)
	private static binding = new Binding()
	
	public static void main(String[] args) {
		try {
			eval("println 'init'")
			PlexusSecurityManager.install()
			PlexusSecurityManager.soleInstance.addSuspiciousThread(Thread.currentThread(), key)
			eval("class bubba {static {println(('/tmp/duh' as File).text)}}")
			eval("println 'script'")
			eval("println 'script2'")
			println readFile('/tmp/duh')
			eval("Sandbox.eval('println \"doy!\"')")
			PlexusSecurityManager.runAuthorized(key) {
				eval("println(('/tmp/duh' as File).text)")
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	def static eval(String str) {
		try {
			def cl = loader.parseClass(str)
			def obj = cl.newInstance()

			obj.setBinding(binding)
			obj.run()
		} catch (Exception ex) {
			println "ERROR DURING EVAL OF [$str]..."
			System.out.flush()
			ex.printStackTrace()
			System.err.flush()
		} catch (Error ex) {
			println "ERROR DURING EVAL OF [$str]..."
			System.out.flush()
			ex.printStackTrace()
			System.err.flush()
		}
		System.out.flush()
		System.err.flush()
	}
	def static readFile(file) {
		def result

		PlexusSecurityManager.runAuthorized(key) {
			result = (file as File).text
		}
		return result
	}
}
