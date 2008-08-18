package p2pmud

public class CloudProperties {
	def properties = [:] as Properties
	def setPropertyHooks = [:]
	def removePropertyHooks = [:]
	def changedPropertyHooks = []
	/**
	 * Pattern for properties which should be saved
	 */
	def persistentPropertyPattern
	def privatePropertyPattern = ''
	def main
	def file

	def CloudProperties(main, file) {
		this.main = main
		this.file = file
	}
	def transmitProperties() {
		def props = [:] as Properties

		properties.each {
			if (!(it.key ==~ privatePropertyPattern)) {
				props[it.key] = it.value
			}
		}
		props
	}
	def each(closure) {
		properties.each(closure)
	}
	def each(pattern, closure) {
		properties.each {
			def match = it.key =~ pattern

			if (match.matches()) {
				closure(it.key, it.value, match)
			}
		}
	}
	def getAt(String key) {
		properties[key as String]
	}
	def putAt(String key, value) {
		def old = properties[key]

		properties[key as String] = value as String
		setPropertyHooks.each {key ==~ it.key && it.value(key, value, old)}
		propertiesChanged(key)
	}
	def removeProperty(key) {
		properties.remove(key)
		removePropertyHooks.each {key ==~ it.key && it.value(key, properties[key])}
		propertiesChanged(key)
	}
	def propertiesChanged(key) {
		if (!key || (persistentPropertyPattern && key ==~ persistentPropertyPattern)) {
			save()
		}
		changedPropertyHooks.each {it()}
		println "NEW PROPERTIES"
		properties.each {
			println "$it.key: $it.value"
		}
	}
	def setProperties(props, saveProps) {
		properties = props
		propertiesChanged(null)
		if (saveProps) {
			save()
		}
	}
	def load() {
		setProperties(file.exists() ? Tools.properties(file) : [:] as Properties, false)
	}
	def save() {
		def saving = []
		def output = file.newOutputStream()

		properties.each {prop ->
			if (persistentPropertyPattern && prop.key ==~ persistentPropertyPattern) {
				saving.add("$prop.key=$prop.value")
			}
		}
		saving.sort()
		output << "#Plexus cloud properties\n#${new Date()}\n"
		saving.each {output << "$it\n"}
		output.close()
	}
}