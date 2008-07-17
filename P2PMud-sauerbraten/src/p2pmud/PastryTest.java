package p2pmud;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collection;
import java.util.Enumeration;

import javolution.util.FastTable;
import rice.Continuation;
import rice.environment.Environment;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastImpl;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.ScribeMultiClient;
import rice.p2p.scribe.Topic;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.persistence.LRUCache;
import rice.persistence.MemoryStorage;
import rice.persistence.PersistentStorage;
import rice.persistence.Storage;
import rice.persistence.StorageManagerImpl;

public class PastryTest implements Application, ScribeMultiClient {
	protected PastryNode node;
	private Endpoint endpoint;
	private ScribeImpl myScribe;
	private Topic myTopic;
	private Environment env;
	private PastImpl past;
	private PastryIdFactory idFactory;
	private InetAddress outgoingAddress;

	private static int mode;

	private static final PastryTest test = new PastryTest();
	private static final int SCRIBE = 0;
	private static final int PAST = 1;
	
	/**
	 * Usage: 
	 * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson1.DistTutorial localbindport bootIP bootPort
	 * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001
	 */
	public static void main(final String[] args) throws Exception {
		try {
			// the port to use locally
			int bindport = Integer.parseInt(args[0]);
			// build the bootaddress from the command line args
			String host = args[1];
			InetAddress bootaddr = null;
			
			faces: for (Enumeration interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
				NetworkInterface face = (NetworkInterface) interfaces.nextElement();
				
				for(Enumeration addrs = face.getInetAddresses(); addrs.hasMoreElements(); ) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					
					if (addr.isAnyLocalAddress()) {
						break;
					} else if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
						test.outgoingAddress = addr;
						System.out.println("Using interface: " + addr);
						break faces;
					}
				}
			}
			bootaddr = host.equals("-") ? test.outgoingAddress : InetAddress.getByName(host);
			if (bootaddr == null) {
				throw new RuntimeException("Could not find interface");
			}
			int bootport = Integer.parseInt(args[2]);
			InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);
			// launch our node!
			test.connect(args, bindport, bootaddress);
			if (args.length > 3) {
				new Thread() {
					public void run() {
						try {
							Thread.sleep(5000);
							mode = SCRIBE;
							for (int i = 3; i < args.length; i++) {
								if (args[i].equals("-scribe")) {
									mode = SCRIBE;
								} else if (args[i].equals("-past")) {
									mode = PAST;
								} else {
									switch (mode) {
									case SCRIBE:
										test.sendMulticast(args[i]);
										break;
									case PAST:
										test.storeData(args[i]);
										break;
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		} catch (Exception e) {
			// remind user how to use
			System.out.println("Usage:"); 
			System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson1.DistTutorial localbindport bootIP bootPort");
			System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001");
			throw e; 
		} 
	}
	
	public void disconnect() {}
	/**
	 * This constructor sets up a PastryNode.  It will bootstrap to an 
	 * existing ring if it can find one at the specified location, otherwise
	 * it will start a new ring.
	 * 
	 * @param bindport the local port to bind to 
	 * @param bootaddress the IP:port of the node to boot from
	 */
	public void connect(String args[], int bindport, InetSocketAddress bootaddress) throws Exception {
		env = new Environment();
//		env.getParameters().setInt("loglevel", Logger.FINE);
		// disable the UPnP setting (in case you are testing this on a NATted LAN)
		env.getParameters().setString("nat_search_policy", "never");
		env.getParameters().setString("probe_for_external_address", "true");
		String probeHost = null;
		int probePort = 0;
		for (int i = 3; i < args.length; i++) {
			if (args[i].equals("-external")) {
				int col = args[++i].indexOf(':');

				probeHost = args[i].substring(0, col);
				probePort = Integer.parseInt(args[i].substring(col + 1));
				env.getParameters().setString("external_address", args[i]);
			}
		}
		NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
		FastTable<InetSocketAddress> boots = new FastTable<InetSocketAddress>();
		FastTable<InetSocketAddress> probes = new FastTable<InetSocketAddress>();
		boots.add(bootaddress);
		if (probeHost != null) {
			probes.add(new InetSocketAddress(probeHost, probePort));
		}
		SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, outgoingAddress, bindport, env);
		node = factory.newNode(probes.get(0));
		node.boot(bootaddress);
		startScribe();
		startPast();
		// the node may require sending several messages to fully boot into the ring
		synchronized (node) {
			while (!node.isReady() && !node.joinFailed()) {
				// delay so we don't busy-wait
				node.wait(500);
				// abort if can't join
				if (node.joinFailed()) {
					throw new IOException("Could not join the FreePastry ring.  Reason:" + node.joinFailedReason());
				}
			}
		}
		System.out.println("Finished creating new node " + node + ", count: " + node.getLeafSet().getUniqueCount());
	}
	private void startScribe() {
		endpoint = node.buildEndpoint(this, "myinstance");
	    myScribe = new ScribeImpl(node,"myScribeInstance");
	    idFactory = new PastryIdFactory(node.getEnvironment());
	    myTopic = new Topic(idFactory, "updates for bubba");
	    endpoint.register();
	    myScribe.subscribe(myTopic, this);
	}
	private void startPast() throws IOException {
		// used for generating PastContent object Ids.
		// this implements the "hash function" for our DHT
		PastryIdFactory idf = new rice.pastry.commonapi.PastryIdFactory(env);
	      
		// create a different storage root for each node
		String storageDirectory = "/tmp/storage"+node.getId().hashCode();
	
		// create the persistent part
		Storage stor = new PersistentStorage(idf, storageDirectory, 4 * 1024 * 1024, node.getEnvironment());
		past = new PastImpl(node, new StorageManagerImpl(idf, stor, new LRUCache(new MemoryStorage(idf), 512 * 1024, node.getEnvironment())), 3, "") {
			public void deliver(Id id, rice.p2p.commonapi.Message message) {
				System.out.println("received PAST message: " + message);
				super.deliver(id, message);
			}
		};
	}
	public void deliver(Id id, rice.p2p.commonapi.Message message) {
		System.out.println("received message: " + message);
	}
	public boolean forward(RouteMessage message) {
		System.out.println("forward message: " + message);
		return true;
	}
	public void update(rice.p2p.commonapi.NodeHandle handle, boolean joined) {
		System.out.println(handle + (joined ? " joined" : " left"));
	}
	public boolean anycast(Topic topic, ScribeContent content) {
		System.out.println("Received anycast: " + content);
		return false;
	}
	public void childAdded(Topic topic, rice.p2p.commonapi.NodeHandle child) {
		System.out.println("CHILD ADDED: " + child);
	}
	public void childRemoved(Topic topic, rice.p2p.commonapi.NodeHandle child) {
		System.out.println("CHILD REMOVED: " + child);
	}
	public void deliver(Topic topic, ScribeContent content) {
		if (content instanceof P2PMudScribeContent) {
			System.out.println("Received p2pmud update: " + content);
		} else {
			System.out.println("Received update: " + content);
		}
	}
	public void subscribeFailed(Topic topic) {
		System.err.println("ERROR: Failed to subscribe to topic: " + topic);
	}
	public void subscribeFailed(Collection topics) {
		System.err.println("ERROR: Failed to subscribe to topics: " + topics);
	}
	public void subscribeSuccess(Collection arg0) {}
	public void sendMulticast(String msg) throws IOException {
		System.out.println("Node "+endpoint.getLocalNodeHandle()+" broadcasting " + msg);
		myScribe.publish(myTopic, new P2PMudScribeContent(msg)); 
	}
	public void storeData(String handle) throws IOException {
		final PastContent myContent = new P2PMudPastContent(idFactory.buildId(handle), handle);
	
		past.insert(myContent, new Continuation() {
			public void receiveResult(Object result) {          
				Boolean[] results = ((Boolean[]) result);
				int numSuccessfulStores = 0;
				for (int ctr = 0; ctr < results.length; ctr++) {
					if (results[ctr].booleanValue()) 
						numSuccessfulStores++;
				}
				System.out.println(myContent + " successfully stored at " + numSuccessfulStores + " locations.");
			}
			public void receiveException(Exception result) {
				System.out.println("Error storing "+myContent);
				result.printStackTrace();
			}
		});
	}
}
