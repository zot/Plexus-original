import net.sbbi.upnp.messages.UPNPResponseException
import net.sbbi.upnp.devices.*;
import net.sbbi.upnp.*;
import static net.sbbi.upnp.Discovery.*;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import p2pmud.P2PMudPeer

public class Plexus {
	def static props
	def static runCount

	public static void main(String[] args) {
		Prep.main()
		args = Prep.mainArgs
		props = Prep.props
		runCount = props.runCount ? Integer.parseInt(props.runCount) + 1 : 0;
		props.runCount = String.valueOf(runCount)
		saveProps()

		System.setProperty('past_storage', props.past_storage)
		if (props.upnp == '1') pokeHole("Plexus", Integer.parseInt(props.external_port))
		if (props.node_interface && props.node_interface != '') P2PMudPeer.node_interface = props.node_interface;
		Test.main(*args)
	}
	public static saveProps() {
		Prep.saveProps()
	}
	
	public static pokeHole(String service, int port) {
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
		    def mapped = testIGD.addPortMapping( "Plexus", null, port, port, localHostIP, 0, "TCP" );
		    if ( mapped ) {
		      System.out.println( "Port $port mapped to " + localHostIP );
		    }
		  }
		} catch ( IOException ex ) {
		  // some IO Exception occured during communication with device
		} catch( UPNPResponseException respEx ) {
		  // oops the IGD did not like something !!
		}		
	}
}
