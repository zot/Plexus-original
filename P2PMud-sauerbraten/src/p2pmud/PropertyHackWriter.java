package p2pmud;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class PropertyHackWriter extends BufferedWriter {
	public boolean active = true;
	public int lineCount = 0;

	public PropertyHackWriter(OutputStream str) {
		super(new OutputStreamWriter(str));
	}
	public void newLine() throws IOException {
		if (active) {
			super.newLine();
		}
		active = lineCount++ != 0;
	}
	public void write(String str) throws IOException {
		if (active) {
			super.write(str);
		}
	}
}
