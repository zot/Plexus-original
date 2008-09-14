class Props {
	def properties = [:] as Properties
	def profile = ""
	def profiles = [] as Set
	def last_profile
	def file

	def setProfile(prof) {
		profile = prof ? "$prof-" : ""
	}
	def getAt(String key) {
		properties["$profile$key" as String]
	}
	def putAt(String key, value) {
		key = "$profile$key" as String
		def old = properties[key]

		properties[key] = value as String
		old
	}
	def propertyMissing(String name) {
		getAt(name)
	}
	def propertyMissing(String name, value) {
		putAt(name, value)
	}
	def store() {
		def output = file.newOutputStream()

		properties.store(output, "Plexus Properties")
		output.close()
	}
	def load() {
		properties = [:] as Properties
		if (file.exists()) {
			def input = new FileInputStream(file)
	
			properties.load(input)
			input.close()
			profiles = [*(properties?.profiles ? properties.profiles.split(',') : [])] as Set
			last_profile = properties?.last_profile
		}
	}
	def setLastProfile(prof) {
		last_profile = properties.last_profile = prof
	}
	def addProfile(prof) {
		if (!profiles.contains(prof)) {
			profiles.add prof
			properties.profiles = profiles.join(',')
			store()
			return true
		} else {
			return false
		}
	}
	def removeProfile() {
		if (profile) {
			def k = []

			k.addAll(properties.keySet())
			k.each {
				if (it ==~ "$profile.*") {
					properties.remove(it)
				}
			}
			profiles.remove(profile.substring(0, profile.length() - 1))
			properties.profiles = profiles.join(',')
			store()
		}
	}
}