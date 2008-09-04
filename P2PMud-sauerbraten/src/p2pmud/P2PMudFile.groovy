package p2pmud

import rice.p2p.past.ContentHashPastContent
import rice.p2p.util.SecurityUtils
import rice.p2p.past.PastContent
import rice.Continuation.MultiContinuation
import rice.Continuation
import rice.p2p.commonapi.Id

public class P2PMudFile extends ContentHashPastContent {
	public ArrayList<Id> chunks
	public int size
	public boolean mutable

	public static chunkSize = 10000

	private static final long serialVersionUID = 1L

	def static File filename(cacheDir, id) {
		id = id instanceof Id ? id.toStringFull() : id
		new File(cacheDir, "${id.substring(0, 2)}/${id.substring(2)}")
	}
	def static ArrayList<PastContent> create(cacheDir, file, mutable, cacheOverride) {
		try {
			def result = []
			def ids = []
			def bytes = file instanceof byte[] ? file : (file as File).readBytes()
			def id = Tools.contentId(bytes)
			def stored = !cacheOverride && filename(cacheDir, id).exists()
			def data = Tools.encode(bytes)

			for (def i = 0; i < data.length(); i += chunkSize) {
				P2PMudFileChunk chunk = new P2PMudFileChunk(id, data.substring(i, Math.min(i + chunkSize, data.length())), ids.size())

				if (!stored) {
					result.add(chunk)
				}
				ids.add(chunk.getId())
			}
			def p2pFile = new P2PMudFile(id, ids, bytes.length);
			p2pFile.mutable = mutable
			result.add(0, p2pFile)
			return result as ArrayList
		} catch (Exception ex) {
			Tools.stackTrace(ex)
			return null;
		}
	}
	/**
	 * sends [P2PMudFile, dirProps] to cont
	 */
	def static storeDir(cacheDir, dir, cont) {
		def props = new Properties()
		def files = []
		def count = 0
		def mcont

		cacheDir = cacheDir as File
		if (dir instanceof Map) {
			for (file in dir) {
println "STORE: $file.key -> $file.value"
				files.add([file.value, file.key])
			}
		} else {
			dir = dir as File
			dir.eachFileRecurse {
				if (it.getName() == "Thumbs.db") {
					println "IGNORING FILE: $it"
				} else {
					println "adding file: $it"
					files.add([it, Tools.subpath(dir, it)])
				}
			}
		}
		println "${files.size()} files"
		mcont = new MultiContinuation([
			receiveResult: {res ->
				for (result in res) {
					if (res instanceof Exception) {
						cont.receiveException(res)
						return
					}
				}
				def stream = new ByteArrayOutputStream()

				println "--- STORING DIR: $props"
				props.store(stream, "directory")
				P2PMudPeer.test.wimpyStoreFile(cacheDir, stream.toByteArray(), [
					receiveResult: {res2 -> cont.receiveResult([file: res2, properties: props])},
					receiveException: {res2 -> cont.receiveException(res2)}
				] as Continuation, false, false)
			},
			receiveException: {res -> cont.receiveException(res)}
		] as Continuation, files.size())
		for (file in files) {
			def c = count++

			println "FILE $c: $file"
			println "PROPS: $props"
			P2PMudPeer.test.wimpyStoreFile(cacheDir, file[0], [
				receiveResult: {res ->
					println "c: $c"
					println "relative file: ${file[1]} = ${res.id.toStringFull()}"
					props[files[c][1]] = res.id.toStringFull()
					println "added prop to props: $props"
					mcont.getSubContinuation(c).receiveResult(res)
				},
				receiveException: {res ->
					mcont.getSubContinuation(c).receiveException(res)
				}
			] as Continuation, false, false);
		}
	}
	/**
	 * cont receives dir as result
	 */
	def static fetchDir(id, cacheDir, dir, cont, mutable) {
		id = id instanceof String ? rice.pastry.Id.build(id) : id
		P2PMudPeer.test.wimpyGetFile(id, cacheDir, [
			receiveResult: {result ->
				def filename = result[0]
				def missing = result[1]
				def file = result[2]

				if (!missing || missing.isEmpty()) {
					def tmpDir = new File(cacheDir, "download/${id.toStringFull()}")
			
					tmpDir.mkdirs()
					P2PMudFile.fetchDirFromProperties(cacheDir, id, Tools.properties(P2PMudFile.filename(cacheDir, id)), tmpDir, [
						receiveResult: {
							dir.getAbsoluteFile().getParentFile().mkdirs()
							tmpDir.renameTo(dir)
							cont.receiveResult(it)
						},
						receiveException: {cont.receiveException(it)}
					] as Continuation, mutable)
				} else {
					cont.receiveException(new Exception("Couldn't load file: $filename"));
				}
			},
			receiveException: {cont.receiveException(new Exception("Error retrieving file: ${id.toStringFull()}", it))}
		] as Continuation)
	}
	/**
	 * cont receives dir as result
	 */
	public static void fetchDirFromProperties(cacheDir, id, props, dir, cont, mutable) {
		def fileNum = 0
		def ids = [:]
		def files = []
		def mcont = new MultiContinuation([
			receiveResult: {o ->
				def succeeded = true
				def temps = []
		
				for (int i = 0; i < o.length; i++) {
					if (o[i] instanceof Exception) {
						System.err.println "Problem loading file '${files[i]}' while fetching directory..."
						o[i].printStackTrace()
						suceeded = false
					} else if (o[i][1]) {
		    			if (succeeded) {
		    				cont.receiveException(new Exception("Could not retrieve file: " + o[i][0]));
		    			}
						succeeded = false
		    		} else {
		    			def cacheFile = o[i][0]
		    			def destFile

		    			synchronized (ids) {
		    				destFile = new File(dir, ids[cacheFile])
		    			}
		    			Tools.copyFile(cacheFile, destFile)
		    			if (o[i][2]?.isMutable()) {
		    				cacheFile.delete()
		    			}
		    		}
		    	}
				if (succeeded) {
		    		cont.receiveResult(dir)
				} else {
					cont.receiveException(new Exception("Could not retrieve directory: " + id));
				}
		    },
		    receiveException: {e ->
		    	cont.receiveException(e)
		    }
		] as Continuation, props.size())

		dir = dir as File
		cacheDir = cacheDir as File
		if (mutable) {
			filename(cacheDir, id).delete()
		}
		if (!dir.exists()) {
			dir.mkdirs()
		}
		for (file in props) {
			synchronized (ids) {
				ids[filename(cacheDir, file.value)] = file.key
			}
			files.add(file.key)
			P2PMudPeer.test.wimpyGetFile(rice.pastry.Id.build(file.value), cacheDir, mcont.getSubContinuation(fileNum++))
		}
	}

	public P2PMudFile(Id id, chunks, size) {
		super(id)
		this.chunks = chunks
		this.size = size
	}
	def String toString() {
		"P2PMudFile ${getId().toStringFull()} (${size} bytes)"
	}
	public boolean isMutable() {
		mutable
	}
	def File filename(cacheDir) {
		filename(cacheDir, id.toStringFull())
	}
	def fetchDir(cacheDir, dir, cont) {
		fetchDirFromProperties(cacheDir, getId(), Tools.properties(filename(cacheDir)), dir, cont)
	}
}
