import java.security.Policy
import sun.security.provider.PolicyFile
import java.security.ProtectionDomain
import java.security.PrivilegedAction
import java.security.AccessController

public class Sandbox {
	private engine = new GroovyScriptEngine(['/tmp/scripts'] as String[], Sandbox.classLoader)
	private binding = new Binding()
	private counter = 0
	private main

	public static void main(String[] args) {
		try {
			def box = new Sandbox(null, '/tmp/scripts')
			box.binding.setVariable('value', 10)
			box.ev('println "value: $value"')
			box.ev('println "Context: ${java.security.AccessController.context}"')
			box.ev("println 'init'")
			def block = box.ev("return {new File('/tmp/blorfl') << 'george'}")
			block()
			System.setSecurityManager(new SecurityManager())
			try {
				block()
			} catch (Exception ex) {
				ex.printStackTrace()
			}
			box.ev('println Sandbox')
			box.ev("class bubba {static {println(('/tmp/duh' as File).text)}}")
			box.ev("println 'script'")
			box.ev("println sandbox.readFile('/tmp/duh')")
			box.ev("sandbox.eval('println \"doy!\"')")
			box.ev("new Duh().run()")
			box.ev("println(('/tmp/duh' as File).text)")
			box.ev("new File('/tmp/floop') << 'fred'")
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	def Sandbox(main, dirs) {
		this.main = main
		engine = new GroovyScriptEngine(dirs as String[], Sandbox.classLoader)
		binding.setVariable('sandbox', this)
		binding.setVariable('mapProps', main.mapProps)
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
	def ev(expr) {
		try {
			return eval(expr)
		} catch (Exception ex) {
			System.err.println("Exception evaluating expression: $expr")
			ex.printStackTrace()
		} catch (Error err) {
			System.err.println("Error evaluating expression: $expr")
			err.printStackTrace()
		}
	}
	def eval(exp) {
		def name = "tmp_${counter++}.groovy"
		def file = "/tmp/scripts/$name" as File

		java.security.AccessController.doPrivileged({
			if (file.exists()) {
				file.delete()
			}
			file << exp
			println "WRITING $exp"
			return null;
		} as java.security.PrivilegedAction);
		exec(name)
	}
	def exec(file) {
		def obj

		java.security.AccessController.doPrivileged({
			obj = engine.createScript(file as String, binding)
		} as java.security.PrivilegedAction);
		obj.run()
	}
	def readFile(file) {
		def result

		java.security.AccessController.doPrivileged({
			result = (file as File).text
		} as java.security.PrivilegedAction);
		return result
	}
}
