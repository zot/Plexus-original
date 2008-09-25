import java.security.Policy
import sun.security.provider.PolicyFile
import java.security.ProtectionDomain
import java.security.PrivilegedAction
import java.security.AccessController

public class Sandbox {
	private static engine = new GroovyScriptEngine(['/tmp/scripts'] as String[], Sandbox.classLoader)
	private static binding = new Binding()
	private static counter = 0

	public static void main(String[] args) {
		try {
			initialize(null)
			binding.setVariable('value', 10)
			ev('println "value: $value"')
			ev('println "Context: ${java.security.AccessController.context}"')
			ev("println 'init'")
			def block = ev("return {new File('/tmp/blorfl') << 'george'}")
			block()
			System.setSecurityManager(new SecurityManager())
			block()
			ev('println Sandbox')
			ev("class bubba {static {println(('/tmp/duh' as File).text)}}")
			ev("println 'script'")
			ev("println Sandbox.readFile('/tmp/duh')")
			ev("Sandbox.evalExpr('println \"doy!\"')")
			ev("new Duh().run()")
			ev("println(('/tmp/duh' as File).text)")
			ev("new File('/tmp/floop') << 'fred'")
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	def static ev(expr) {
		try {
			return evalExpr(expr)
		} catch (Exception ex) {
			System.err.println("Exception evaluating expression: $expr")
			ex.printStackTrace()
		} catch (Error err) {
			System.err.println("Error evaluating expression: $expr")
			err.printStackTrace()
		}
	}
	def static initialize(main) {
		binding.setVariable('main', main)
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
	def static evalExpr(exp) {
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
		eval(name)
	}
	def static eval(file) {
		def obj

		java.security.AccessController.doPrivileged({
			obj = engine.createScript(file as String, binding)
		} as java.security.PrivilegedAction);
		obj.run()
	}
	def static readFile(file) {
		def result

		java.security.AccessController.doPrivileged({
			result = (file as File).text
		} as java.security.PrivilegedAction);
		return result
	}
}
