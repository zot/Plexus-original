import net.sbbi.upnp.devices.*;
import net.sbbi.upnp.*;
import static net.sbbi.upnp.Discovery.*;

def face;

for (i in NetworkInterface.getNetworkInterfaces()) {
	if (!face) {
		face = i
	}
//	println i
}
println "Face: $face"
UPNPRootDevice[] devices = Discovery.discover( 5000, DEFAULT_TTL, DEFAULT_MX, DEFAULT_SEARCH, face)
//UPNPRootDevice[] devices = Discovery.discover();
if (devices) {
	for ( int i = 0; i < devices.length; i++ ) {
		System.out.println( "Found device " + devices[i].getModelName() );
	}
} else {
	println "No devices found"
}
