package p2pmud

import rice.p2p.past.ContentHashPastContent
import rice.p2p.util.SecurityUtils
import rice.p2p.past.PastContent
import rice.p2p.commonapi.Id
import rice.Continuation.MultiContinuation
import rice.Continuation

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
	def static ArrayList<PastContent> create(cacheDir, filename, mutable) {
		try {
			def result = []
			def ids = []
			def bytes = (filename as File).readBytes()
			def id = Tools.contentId(bytes)
			def stored = filename(cacheDir, id).exists()
			def data = Tools.encode(bytes)

			for (def i = 0; i < data.length(); i += chunkSize) {
				P2PMudFileChunk chunk = new P2PMudFileChunk(id, data.substring(i, Math.min(i + chunkSize, data.length())), ids.size())

				if (!stored) {
					result.add(chunk)
				}
				ids.add(chunk.getId())
			}
			def file = new P2PMudFile(id, ids, bytes.length);
			file.mutable = mutable
			result.add(0, file)
			return result as ArrayList
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public P2PMudFile(Id id, chunks, size) {
		super(id)
		this.chunks = chunks
		this.size = size
	}
	def String toString() {
		"P2PMudFile $branch ($path) ${getId().toStringFull()}"
	}
	public boolean isMutable() {
		mutable
	}
	def File filename(cacheDir) {
		filename(cacheDir, id.toStringFull())
	}
	def fetchDir(cacheDir, baseDir, cont) {
		def fileNum = 0
		def dir = new File(baseDir, id.toStringFull())
		def props = Tools.properties(filename(cacheDir))
		def ids = [:]
		def mcont = new MultiContinuation([
			receiveResult: {o ->
				def succeeded = true
				def temps = []
		
				for (int i = 0; i < o.length; i++) {
		    		if (o[i][1]) {
		    			if (succeeded) {
		    				cont.receiveException(new Exception("Could not retrieve file: " + o[i][0]));
		    			}
						succeeded = false
		    		} else {
		    			def cacheFile = o[i][0]
		    			def destFile

		    			synchronized (ids) {
		    				destFile = new File(baseDir, ids[cacheFile])
		    			}
		    			Tools.copyFile(cacheFile, destFile)
		    			if (o[i][2]?.isMutable()) {
		    				cacheFile.delete()
		    			}
		    		}
		    	}
				if (succeeded) {
		    		cont.receiveResult(baseDir)
				} else {
					cont.receiveException(new Exception("Could not retrieve directory: " + id));
				}
		    },
		    receiveException: {e ->
		    	cont.receiveException(e);
		    }
		] as Continuation, props.size)

		if (!dir.exists()) {
			dir.mkdirs()
		}
		for (file in props) {
			synchronized (ids) {
				ids[filename(cacheDir, file.value)] = file.key
			}
			main.peer.wimpyGetFile(Id.build(file.value), cacheDir, cont.getSubContinuation(fileNum++))
		}
	}
	def storeDir(cacheDir, baseDir, cont) {
		def props = new Properties()
		def files = []
		def count = 0
		def mcont

		baseDir.eachFileRecurse {
			files.add(it)
		}
		mcont = new MultiContinuation([
			receiveResult: {
				def dirFile = new File(baseDir, "tmp-${System.currentTimeMillis()}")

				Tools.store(props, dirFile, "directory")
				P2PMudPeer.test.wimpyStoreFile(cacheDir, dirFile, [
					receiveResult: {
						dirFile.delete()
						cont.receiveResult(it)
					},
					receiveException: {
						cont.receiveException(it)
					}
				] as Continuation, false)
			},
			receiveException: {
				cont.receiveException(it)
			}
		] as Continuation, files.size())
		for (file in files) {
			def c = count++

			P2PMudPeer.test.wimpyStoreFile(cacheDir, file, [
				receiveResult: {
					props[files[c]] = it.id.toStringFull()
					mcont.getSubContinuation(c).receiveResult(it)
				},
				receiveException: {
					mcont.getSubContinuation(c).receiveException(it)
				}
			] as Continuation, false);
		}
	}
}
