import net.sbbi.upnp.messages.UPNPResponseExceptionimport net.sbbi.upnp.devices.*;
import net.sbbi.upnp.*;
import static net.sbbi.upnp.Discovery.*;
import net.sbbi.upnp.impls.InternetGatewayDevice;

int discoveryTimeout = 5000; // 5 secs to receive a response from devices
try {
  def IGDs = InternetGatewayDevice.getDevices( discoveryTimeout );
  if ( IGDs != null ) {
    // let's the the first device found
    def testIGD = IGDs[0];
    System.out.println( "Found device " + testIGD.getIGDRootDevice().getModelName() );
    // now let's open the port
    String localHostIP = InetAddress.getLocalHost().getHostAddress();
    // we assume that localHostIP is something else than 127.0.0.1
    def mapped = testIGD.addPortMapping( "Plexus", 
                                             null, 9190, 9190,
                                             localHostIP, 0, "TCP" );
    if ( mapped ) {
      System.out.println( "Port 9190 mapped to " + localHostIP );
      // and now close it
      def unmapped = testIGD.deletePortMapping( null, 9190, "TCP" );
      if ( unmapped ) {
        System.out.println( "Port 9190 unmapped" );
      }
    }
  }
} catch ( IOException ex ) {
  // some IO Exception occured during communication with device
} catch( UPNPResponseException respEx ) {
  // oups the IGD did not like something !!
}

/*
def face;

for (i in NetworkInterface.getNetworkInterfaces()) {
	if (!face && !i.isLoopback()) {
		face = i
	}
	// println i
}
println "Face: $face"
//UPNPRootDevice[] devices = Discovery.discover(10000, DEFAULT_TTL, DEFAULT_MX, DEFAULT_SEARCH, face) // ROOT_DEVICES, face)
UPNPRootDevice[] devices = Discovery.discover();
if (devices) {
	for ( int i = 0; i < devices.length; i++ ) {
		System.out.println( "Found device " + devices[i].getModelName() );
	}
} else {
	println "No devices found"
}
*/
