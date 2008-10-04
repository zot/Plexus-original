import java.security.SecureRandom
import java.awt.Frame
import javax.swing.JButton
import java.awt.event.ActionListener
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JDialog
import javax.swing.border.TitledBorder
import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import p2pmud.Tools
import net.sbbi.upnp.messages.UPNPResponseException
import net.sbbi.upnp.devices.*;
import net.sbbi.upnp.*;
import static net.sbbi.upnp.Discovery.*;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import p2pmud.P2PMudPeer

public class LaunchPlexus {
	def static props
	def static runCount

	public static void main(String[] args) {
		try {
			checkJavaVersion()
			System.properties["sun.java2d.d3d"] = "false"
			if (System.properties['os.name'] != 'Linux') {
				//System.properties["sun.java2d.opengl"] = "true"
			}
			Prep.main()
			args = Prep.mainArgs
			props = Prep.props
			runCount = props.runCount ? Integer.parseInt(props.runCount) + 1 : 0;
			props.runCount = String.valueOf(runCount)
			if (props.upnp == '1') pokeHole("Plexus", Integer.parseInt(props.external_port))
			if (props.node_interface && props.node_interface != '') P2PMudPeer.node_interface = props.node_interface;
			Plexus.main(*args)
		} catch (Exception ex) {
			Plexus.err('', ex)
		}
	}
	public static saveProps() {
		Prep.saveProps()
	}
	//No SwingBuilder here in case we're using JRE 1.5
	public static checkJavaVersion() {
		if (!System.properties['supress_java_version_check']) {
			def version = System.properties['java.version']
			if (!version.startsWith('1.6.0_10')) {
				def dialog = new JDialog((Frame)null, true)
				def panel = new JPanel(new MigLayout('fill'))
				dialog.setContentPane(panel)
				def group = new JPanel(new MigLayout('fill'))
				panel.add(group, 'grow, spanx, wrap')
				group.setBorder(new TitledBorder('Plexus Needs At Least Java Version 1.6.0_10'))
				group.add(new JLabel("You are running with Java version $version"), 'wrap, spanx')
				def cont = new JButton("Continue Anyway")
				cont.addActionListener({e -> dialog.visible = false} as ActionListener)
				panel.add(cont)
				panel.add(new JPanel(), 'growx')
				def exit = new JButton("Exit")
				exit.addActionListener({e-> System.exit(0)} as ActionListener)
				panel.add(exit)
				dialog.pack()
				dialog.visible = true
			}
		}
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
		      System.out.println( "TCP Port $port mapped to " + localHostIP );
		    }
		    mapped = testIGD.addPortMapping( "Plexus", null, port, port, localHostIP, 0, "UDP" );
		    if ( mapped ) {
		      System.out.println( "UDP Port $port mapped to " + localHostIP );
		    }
		  }
		} catch ( IOException ex ) {
		  // some IO Exception occured during communication with device
		} catch( UPNPResponseException respEx ) {
		  // oops the IGD did not like something !!
		}		
	}
}
