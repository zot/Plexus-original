
import java.security.Policy
import sun.security.provider.PolicyFile
import java.security.ProtectionDomain
import java.security.PrivilegedAction
import java.security.AccessController

public class Sandbox {
	public engine = new GroovyScriptEngine(['/tmp/scripts'] as String[], Sandbox.classLoader)
	public binding = new Binding()
	public counter = 0
	private main
	
	public static void main(String[] args) {
		try {
			def box = new Sandbox(null, ['/tmp/scripts'])
			def block

			java.security.AccessController.doPrivileged({
				box.binding.setVariable('value', 10)
				box.ev('println "value: $value"')
				box.ev('println "Context: ${java.security.AccessController.context}"')
				box.ev("println 'init'")
				println(new ArrayList().elementData)
				block = box.ev("println 'BLORFL'; return {new File('/tmp/blorfl') << 'george'}")
				try {
					block()
				} catch (Exception ex) {
					ex.printStackTrace()
				}
			} as java.security.PrivilegedAction)
			box.ev("println(new ArrayList().elementData)")
			if (block) {
				try {
					block()
				} catch (Exception ex) {
					ex.printStackTrace()
				}
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
		main && binding.setVariable('mapProps', main.mapProps)
		main && binding.setVariable('globalProps', main.globalProps)
		def output = ('/tmp/policy' as File).newOutputStream()
		def input = Sandbox.getResourceAsStream('/resources/java.policy')
	
		output << input
		input.close()
		output << "\n"
		Sandbox.classLoader.getURLs().each {
		 	output << "grant codeBase \"$it\" {\npermission java.security.AllPermission;\n};\n"
		}
		output.close()
		Policy.setPolicy(new PolicyFile(new URL("file:/tmp/policy")))
		System.setSecurityManager(new SecurityManager())
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
//			println "WRITING $exp"
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
