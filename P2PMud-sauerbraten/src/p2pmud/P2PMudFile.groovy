package p2pmud;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import rice.p2p.commonapi.Id;
import rice.p2p.past.ContentHashPastContent;

/**
 * Tempory file representation until we switch to the Git model
 * 
 */
public class P2PMudFile extends ContentHashPastContent {
	public String path
	public String branch
	public byte[] data

	private static final long serialVersionUID = 1L
	
	public static P2PMudFile create(Id id, String branch, File base, String path) {
		try {
			byte[] data = new File(base, path).readBytes();

			return new P2PMudFile(id, branch, path, data);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public P2PMudFile(Id id, String branch, String path, byte[] data) {
		super(id)
		this.branch = branch
		this.path = path
		this.data = data
	}
	public String toString() {
		return "P2PMud File: " + path
	}
}
