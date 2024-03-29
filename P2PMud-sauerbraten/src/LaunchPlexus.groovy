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
	def static poked = [] as Set
	def static igd
	def static gatewayIp
	def static privateIp
	def static portMappings = []

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
	public static getIgd() {
		println "GETTING IGD"
		if (!igd) {
			def IGDs = InternetGatewayDevice.getDevices(1500);
			if ( IGDs != null ) {
				// let's get the first device found
				igd = IGDs[0];
			    println( "Found device " + igd.getIGDRootDevice().getModelName() );
				if (!gatewayIp) {
					gatewayIp = igd.externalIPAddress
					privateIp = isPrivateIP(gatewayIp)
					println "REMOTE HOST: ${igd.remoteHost}"
				}
			}
		}
		igd
	}
	/**
	 * reserved ranges:
	 * 10.x.x.x
	 * 169.x.x.x
	 * 172.16.x.x - 172.31.x.x
	 * 192.168.x.x
	 */
	public static isPrivateIP(ip) {
		ip ==~ /((10|169)|172\.(1[6-9]|2.|30|31)|192.168)\..*/
	}
	public static pokeHole(String service, int port) {
		if (!(port in poked)) {
			poked.add(port)
			try {
				def device = getIgd()

				if (device) {
					def localHostIP = InetAddress.getLocalHost().getHostAddress();

					println "externalIp: ${getGatewayIp()}"
					pokeIgdHole(device, port, localHostIP)
				} else {
					println("Couldn't find UPNP capable device");
				}
			} catch ( IOException ex ) {
				// some IO Exception occured during communication with device
				ex.printStackTrace()
			} catch( UPNPResponseException respEx ) {
				// oops the IGD did not like something !!
				respEx.printStackTrace()
			}
		}
	}
	public static pokeIgdHole(device, port, destIP) {
	    // we assume that destIP is something else than 127.0.0.1
	    println "ADDING TCP PORT MAPPING $port -> $destIP"
	    def mapped = igd.addPortMapping( "Plexus", null, port, port, destIP, 0, "TCP" );

	    if ( mapped ) {
			System.out.println( "TCP Port $port mapped to " + destIP );
			portMappings << [device: device, port: port, protocol: "TCP"]
	    } else {
			System.out.println( "COULD NOT MAP TCP Port $port to " + destIP );
	    }
	    println "ADDING UDP PORT MAPPING $port -> $destIP"
	    mapped = igd.addPortMapping( "Plexus", null, port, port, destIP, 0, "UDP" );
	    if ( mapped ) {
			System.out.println( "UDP Port $port mapped to " + destIP );
			portMappings << [device: device, port: port, protocol: "UDP"]
	    } else {
			System.out.println( "COULD NOT MAP UDP Port $port to " + destIP );
	    }
	}
	public static cleanMappings() {
		portMappings.each {
			println "attempting to remove mapping: $it.port [$it.protocol]..."
			try {
				if (it.device.deletePortMapping(null, it.port, it.protocol)) {
					println "Succeeded"
				} else {
					println "Couldn't remove mapping $it"
				}
			} catch (Exception ex) {
				pritnln "Exception while attempting to remove mapping $t"
				ex.printStackTrace()
			}
		}
	}
	public static cleanAllMappings() {
		if (igd) {
			try {
				def count
				def mappings = []
	
				try {
					count = igd.getNatMappingsCount()
				} catch (UPNPResponseException ex) {
					count = 200
				}
				println "Checking the first $count entries"
				for (def i = 0; i < count; i++) {
					try {
						def entry = igd.getGenericPortMappingEntry(i)
	
						if (entry.getOutActionArgumentValue('NewPortMappingDescription') == 'Plexus') {
							mappings << entry
						}
					} catch (UPNPResponseException ex) {}
					if (i > 0 && i % 50 == 0) {
						println ''
					}
					print '.'
				}
				println ''
				mappings.each {
					println "deleting old mapping: ${it.getOutActionArgumentValue('NewExternalPort')}"
					igd.deletePortMapping(null, Integer.parseInt(it.getOutActionArgumentValue('NewExternalPort')), it.getOutActionArgumentValue('NewProtocol'))
				}
			} catch ( IOException ex ) {
			  // some IO Exception occured during communication with device
				ex.printStackTrace()
			} catch( UPNPResponseException respEx ) {
			  // oops the IGD did not like something !!
				respEx.printStackTrace()
			}
		}
	}
}
