
public class GroovyFileFilter extends javax.swing.filechooser.FileFilter {
	def description
	def filter

	def GroovyFileFilter(description, filter) {
		this.description = description
		this.filter = filter
	}
	public boolean accept(File f) {
		filter(f)
	}
	public String getDescription() {
		description
	}
}