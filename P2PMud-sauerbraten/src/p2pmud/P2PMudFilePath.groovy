package p2pmud;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import rice.p2p.commonapi.Id;
import rice.p2p.past.ContentHashPastContent;

/**
 * Tempory file path representation until we switch to the Git model
 * 
 */
public class P2PMudFilePath extends ContentHashPastContent {
	public String path
	public Id fileId;

	private static final long serialVersionUID = 1L
	
	public P2PMudFilePath(Id id, String path, Id fileId) {
		super(id)
		this.path = path
		this.fileId = fileId
	}
	public String toString() {
		return "P2PMudFileId: $path (${fileId.toStringFull()})"
	}
}
