import java.security.Policy
import sun.security.provider.PolicyFile
import java.security.ProtectionDomain
import java.security.PrivilegedAction
import java.security.AccessController
import p2pmud.PlexusSecurityManager

public class Sandbox {
	private static engine = new GroovyScriptEngine(['/tmp/scripts'] as String[], Sandbox.classLoader)
	private static binding = new Binding()
	private static counter = 0
	
	public static void main(String[] args) {
		try {
			installPolicy()
			eval('println "Context: ${java.security.AccessController.context}"')
			eval("println 'init'")
			eval("new File('/tmp/blorfl') << 'george'")
			System.setSecurityManager(new SecurityManager())
			eval('println Sandbox')
			eval("class bubba {static {println(('/tmp/duh' as File).text)}}")
			eval("println 'script'")
			eval("println Sandbox.readFile('/tmp/duh')")
			eval("Sandbox.eval('println \"doy!\"')")
			eval("new Duh().run()")
			eval("println(('/tmp/duh' as File).text)")
			eval("new File('/tmp/floop') << 'fred'")
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	def static installPolicy() {
		def output = ('/tmp/policy' as File).newOutputStream()
		def perms = """	permission java.lang.RuntimePermission "accessClassInPackage.sun.reflect";
	permission java.lang.RuntimePermission "accessDeclaredMembers";
	permission java.lang.RuntimePermission "createClassLoader";
	permission groovy.security.GroovyCodeSourcePermission "/groovy/script";
	permission groovy.security.GroovyCodeSourcePermission "/groovy/shell";
	permission java.util.PropertyPermission "file.encoding", "read";
	permission java.util.PropertyPermission "groovyjarjarantlr.ast", "read";
	permission java.util.PropertyPermission "groovy.ast", "read";
	permission java.io.FilePermission "/tmp/scripts/-", "read, write, delete";
	permission java.io.FilePermission "/groovy/script", "read";"""
		def input = Sandbox.getResourceAsStream('/resources/java.policy')

		output << input
		input.close()
		output << "\n"
		Sandbox.classLoader.getURLs().each {
//			 output << "grant codeBase \"$it\" {\n$perms\n};\n"
			 output << "grant codeBase \"$it\" {\npermission java.security.AllPermission;\n};\n"
		}
		output.close()
		Policy.setPolicy(new PolicyFile(new URL("file:/tmp/policy")))
	}
	def static eval(String str) {
		try {
			def name = "tmp_${counter++}.groovy"
			def file = "/tmp/scripts/$name" as File

			java.security.AccessController.doPrivileged({
				if (file.exists()) {
					file.delete()
				}
				file << str
				println "WRITING $str"
				return null;
			} as java.security.PrivilegedAction);
			def obj
			java.security.AccessController.doPrivileged({
				obj = engine.createScript(name, binding)
			} as java.security.PrivilegedAction);
			obj.run()
		} catch (Error ex) {
			println "ERROR DURING EVAL OF [$str]..."
			System.out.flush()
			ex.printStackTrace()
			System.err.flush()
		} catch (Exception ex) {
			println "ERROR DURING EVAL OF [$str]..."
			System.out.flush()
			ex.printStackTrace()
			System.err.flush()
		}
//		System.out.flush()
//		System.err.flush()
	}
	def static readFile(file) {
		def result

		java.security.AccessController.doPrivileged({
			result = (file as File).text
		} as java.security.PrivilegedAction);
		return result
	}
}
