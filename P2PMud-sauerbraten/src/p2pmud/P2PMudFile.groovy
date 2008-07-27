package p2pmud

import rice.p2p.past.ContentHashPastContentimport rice.p2p.util.SecurityUtilsimport rice.p2p.past.PastContentimport rice.p2p.commonapi.Idpublic class P2PMudFile extends ContentHashPastContent {
	public ArrayList<Id> chunks
	public path
	public branch
	
	public static chunkSize = 20000

	public static ArrayList<PastContent> create(branch, base, path) {
		try {
			def result = []
			def ids = []
			def data = Tools.encode(new File(base as File, path as String).readBytes());
			def id = Tools.contentId(data.getBytes())

			for (def i = 0; i < data.length(); i += chunkSize) {
				P2PMudFileChunk chunk = new P2PMudFileChunk(id, data.substring(i, Math.min(i + chunkSize, data.length())), ids.size())

				result.add(chunk)
				ids.add(chunk.getId())
			}
			def file = new P2PMudFile(id, branch, path, ids);
			result.add(file)
			return result as ArrayList
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public P2PMudFile(Id id, branch, path, chunks) {
		super(id)
		this.branch = branch
		this.path = path
		this.chunks = chunks
	}
	def String toString() {
		"P2PMudFile $branch ($path) ${getId().toStringFull()}"
	}
}
