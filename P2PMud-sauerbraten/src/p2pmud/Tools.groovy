package p2pmud

def static deleteAll(file) {
	def f = file as File

	if (f.exists()) {
		if (f.isDirectory()) {
			f.eachFile {child ->
				deleteAll(child)
			}
		}
		println "Deleting: $f"
		f.delete()
	}
}