class Props {
	def properties = [:] as Properties
	def profile = ""
	def profiles = [] as Set
	def last_profile = ""
	def file
	def static defaultProps = [
		sauer_port: '12345',
		name: 'bubba-' + System.currentTimeMillis(),
		guild: '',
		pastry_port_start: '3993',
		pastry_port_end: '3999',
		pastry_boot_host: 'plubble.com',
		pastry_boot_start: '3993',
		pastry_boot_end: '3994',
		external_ip: '',
		external_port: '3993',
		headless: '0',
		upnp: '1',
		sauer_mode: 'launch',
		past_storage:'/tmp/storage-9090'
	] as Properties


	def initProps() {
		println "going to initprops()"
		for (e in defaultProps) {
			if (!this[e.key]) this[e.key] = e.value
		}
		if (!this['sauer_cmd']) {
			if (System.getProperty('os.name').equalsIgnoreCase('linux')) {
				this.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_linux -t'
			} else {
				this.sauer_cmd = 'packages/plexus/dist/sauerbraten_plexus_windows.exe -t'
			}
		}
	}
	
	def setProfile(prof) {
		profile = prof ? "$prof-" : ""
		initProps()
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
		def dir = new File('').getAbsoluteFile()
		def plexusdir = new File(dir, 'packages/plexus')
		this.file = new File(plexusdir, 'plexus.properties')
		
		properties = [:] as Properties
		if (file.exists()) {
			def input = new FileInputStream(file)
	
			properties.load(input)
			input.close()
			profiles = [*(properties?.profiles ? properties.profiles.split(',') : [])] as Set
			if (properties.last_profile == null) properties.last_profile = ""
			last_profile = properties.last_profile
		}
		initProps()
	}
	def setLastProfile(prof) {
		if (prof != last_profile) {
			last_profile = properties.last_profile = prof
		}
	}
	def containsProfile(prof) {
		return profiles.contains(prof);
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