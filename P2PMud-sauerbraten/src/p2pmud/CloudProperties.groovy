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
	def main
	def file

	def CloudProperties(main, file) {
		this.main = main
		this.file = file
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
		def values = value.split(' ')
		def old = properties[key]

		properties[key as String] = value as String
		setPropertyHooks.each {key ==~ it.key && it.value(key, values, old)}
		propertiesChanged(key)
	}
	def removeProperty(key) {
		def values = properties[key]?.split(' ')

		properties.remove(key)
		removePropertyHooks.each {key ==~ it.key &&  it.value(key, values)}
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
		propertiesChanged()
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