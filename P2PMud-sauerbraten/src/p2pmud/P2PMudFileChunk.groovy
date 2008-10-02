package p2pmud;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import rice.p2p.past.rawserialization.JavaSerializedPastContent
import rice.p2p.past.ContentHashPastContent
import rice.p2p.past.PastContent
import rice.p2p.past.PastException

import rice.p2p.commonapi.Id;

/**
 * Tempory file representation
 * need to switch to raw content
 */
public class P2PMudFileChunk extends ContentHashPastContent {
 	public Id file
	public String data
	public int offset

	private static final long serialVersionUID = 1L
	
	public P2PMudFileChunk(Id file, String data, int offset) {
		super(Tools.contentId(("CHUNK: " + data).getBytes()))
		this.data = data
		this.file = file
		this.offset = offset
	}
	public String toString() {
		return "P2PMud Chunk[" + offset + "] ${getId().toStringFull()}"
	}
	//Duplicate file chunks are fine
	public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
		return this;
	}
}
