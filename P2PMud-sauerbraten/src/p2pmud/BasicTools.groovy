package p2pmud

import org.codehaus.groovy.runtime.StackTraceUtils
import java.util.zip.ZipFile

public class BasicTools {
	def static stackTrace(ex) {
		def w = new PrintWriter(System.err)
		def lines = []

//		ex.printStackTrace()
		w.println(ex)
		addSanitizedStackTrace(ex, lines)
		lines.each {
			w.println it
		}
		w.flush()
	}
    def static addSanitizedStackTrace(t, lines) {
        if (t.getCause()) {
        	addSanitizedStackTrace(t.getCause(), lines)
        	lines.add(0, "caused by: $t")
        }
        def trace = StackTraceUtils.sanitize(t).getStackTrace();
        for (int i = 0; i < trace.length; i++) {
        	def element = trace[i];

            lines.add(i, "\tat " + element.className + "."+ element.methodName + "(" + element.fileName + ":" + element.lineNumber + ")");
        }
    }
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
	def static hexDigit(num) {
		(char)(num > 9 ? num - 10 + (int)'A' : num + (int)'0')
	}
	def static hexForIp(ip) {
		def buf = ""

		ip.split(/\./).each {
			def i = Integer.parseInt(it)

			buf += hexDigit((i >> 4) & 0xF)
			buf += hexDigit(i & 0xF)
		}
		buf
	}
	def static properties(file) {
		def input = (file as File).newInputStream()
		def props = new Properties()

		props.load(input)
		input.close()
		props
	}
	def static store(properties, file, comment) {
		def output = (file as File).newOutputStream()

		properties.store(output, comment)
		output.close()
	}
	def static copyAll(from, to) {
		from = from as File
		to = to as File
		if (!from.exists()) return
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
		to = to as File
		if (to.exists() && to.isDirectory()) {
			throw new RuntimeException("Cannot copy file as directory");
		}
		if (from instanceof byte[]) {
			from = new ByteArrayInputStream(from)
		}
		if (!(from instanceof InputStream)) {
			from = (from as File).newInputStream()
		}
		copyStreamToFile(from, to)
		from.close()
	}
	def static subpath(file, child) {
		child.getAbsolutePath().substring(file.getAbsolutePath().length() + 1).replace('\\', '/')
	}
	def static copyStreamToFile(fromStr, to) {
		def tostream = (to as File).newOutputStream()
		def bytes = new byte[10240]
		def count

		while ((count = fromStr.read(bytes)) > -1) {
			tostream.write(bytes, 0, count)
		}
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

	def static guard(block) {
		return {r->
			try {
				block(r)
			} catch (e) {
				e.printStackTrace()
			}
		}
	}

	
	def static getCwd() {
		return new File('').getAbsoluteFile()
	}
}
