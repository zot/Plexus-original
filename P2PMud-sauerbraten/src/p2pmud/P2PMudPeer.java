package p2pmud;

import java.io.File;
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

public class P2PMudPeer implements Application, ScribeMultiClient {
	protected PastryNode node;
	private Endpoint endpoint;
	private ScribeImpl myScribe;
	private Topic myTopic;
	private Environment env;
	private PastImpl past;
	private PastryIdFactory idFactory;
	private InetAddress outgoingAddress;
	private Id other;

	private static int mode;
	private static P2PMudCommandHandler cmdHandler;

	private static final P2PMudPeer test = new P2PMudPeer();
	private static final int SCRIBE = 0;
	private static final int PAST = 1;
	private static final int CMD = 2;

	public static void main(P2PMudCommandHandler handler, String args[]) throws Exception {
		cmdHandler = handler;
		main(args);
	}
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
			InetSocketAddress bootaddress = host.equals("-") ? null : new InetSocketAddress(bootaddr,bootport);
			for (int i = 3; i < args.length; i++) {
				if (args[i].equals("-clean") && System.getProperty("past.storage") != null) {
					File storage = new File(System.getProperty("past.storage"));

					if (storage.exists()) {
						System.out.println("CLEANING STORAGE");
						Tools.deleteAll(storage);
					}
				}
			}
			// launch our node!
			test.connect(args, bindport, bootaddress);
			if (args.length > 3) {
				new Thread() {
					public void run() {
						int count = 0;

						try {
							Thread.sleep(5000);
							mode = SCRIBE;
							for (int i = 3; i < args.length; i++) {
								if (args[i].equals("-scribe")) {
									mode = SCRIBE;
								} else if (args[i].equals("-past")) {
									mode = PAST;
								} else if (args[i].equals("-cmd")) {
									mode = CMD;
								} else if (args[i].equals("-clean")) {
								} else {
									switch (mode) {
									case SCRIBE:
										test.broadcastCmds(new String[]{args[i]});
										break;
									case PAST:
										File base = new File("/tmp/testbase");
										String version = "branch-" + count++;

										base.mkdirs();
										test.wimpyStoreFile(new P2PMudFile(test.idFactory.buildId(version), "branch", version, args[i].getBytes()), new Continuation() {
											public void receiveResult(Object result) {
												if (result != null) {
													System.out.println("Stored file: " + result);
												} else {
													System.out.println("Couldn't store file: branch");
												}
											}
											public void receiveException(Exception exception) {
												System.err.println("Error while attempting to store file: branch");
												exception.printStackTrace();
											}
										});
										break;
									case CMD:
										cmdHandler = new P2PMudCommandHandler() {
											public void handleCommand(P2PMudCommand cmd) {
												System.out.println("Received " + cmd.msgs.length + " commands...");
												for (String m : cmd.msgs) {
													System.out.println(m);
												}
											}
										};
										test.sendCmds(new String[]{args[i]});
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

	public Id buildId(String path) {
		return idFactory.buildId(path);
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
		env.getParameters().setInt("p2p_past_messageTimeout", 60000);
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
		FastTable<InetSocketAddress> probes = new FastTable<InetSocketAddress>();
		if (probeHost != null) {
			probes.add(new InetSocketAddress(probeHost, probePort));
		}
		SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, outgoingAddress, bindport, env);
		if (probes.isEmpty()) {
			node = factory.newNode();
		} else {
			node = factory.newNode(probes.get(0));
		}
		node.boot(bootaddress);
		startScribe();
		startPast();
		System.out.println("Waiting to join ring...");
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
	private void getOther() {
		if (other == null) {
			if (node.getLeafSet().iterator().hasNext()) {
				other = node.getLeafSet().iterator().next().getId();
			}
		}
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
		// create a different storage root for each node
		String storageDirectory = System.getProperty("past.storage", "/tmp/storage"+node.getId().hashCode());
	
		// create the persistent part
		Storage stor = new PersistentStorage(idFactory, storageDirectory, 4 * 1024 * 1024, node.getEnvironment());
		past = new PastImpl(node, new StorageManagerImpl(idFactory, stor, new LRUCache(new MemoryStorage(idFactory), 512 * 1024, node.getEnvironment())), 3, "") {
			public void deliver(Id id, rice.p2p.commonapi.Message message) {
				System.out.println("received PAST message: " + message);
				super.deliver(id, message);
			}
		};
	}
	public void deliver(Id id, rice.p2p.commonapi.Message message) {
		if (message instanceof P2PMudMessage) {
			handleCommand(((P2PMudMessage)message).cmd);
		} else {
			System.out.println("received message: " + message);
		}
	}
	public boolean forward(RouteMessage message) {
		System.out.println("forward message: " + message);
		return true;
	}
	public void update(rice.p2p.commonapi.NodeHandle handle, boolean joined) {
		System.out.println(handle + (joined ? " joined" : " left"));
	}
	public boolean anycast(Topic topic, ScribeContent content) {
		if (content instanceof P2PMudScribeContent) {
			handleCommand(((P2PMudScribeContent)content).cmd);
		} else {
			System.out.println("Received anycast: " + content);
		}
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
			handleCommand(((P2PMudScribeContent)content).cmd);
		} else {
			System.out.println("Received update: " + content);
		}
	}
	protected void handleCommand(P2PMudCommand mudCommand) {
		if (cmdHandler != null && !mudCommand.from.equals(node.getId())) {
			other = mudCommand.from;
			cmdHandler.handleCommand(mudCommand);
		}
	}
	public void subscribeFailed(Topic topic) {
		System.err.println("ERROR: Failed to subscribe to topic: " + topic);
	}
	public void subscribeFailed(Collection topics) {
		System.err.println("ERROR: Failed to subscribe to topics: " + topics);
	}
	public void subscribeSuccess(Collection arg0) {}
	public void anycastCmds(String cmds[]) {
		myScribe.anycast(myTopic, new P2PMudScribeContent(new P2PMudCommand(node.getId(), cmds))); 
	}
	public void broadcastCmds(String cmds[]) {
		myScribe.publish(myTopic, new P2PMudScribeContent(new P2PMudCommand(node.getId(), cmds))); 
	}
	public void sendCmds(String cmds[]) {
		getOther();
		if (other != null) {
			sendCmds(other, cmds);
		}
	}
	public void sendCmds(Id id, String cmds[]) {
		endpoint.route(id, new P2PMudMessage(new P2PMudCommand(endpoint.getId(), cmds)), null);
	}
	public void wimpyStoreFile(final String branchName, File base, final String path, final Continuation cont) {
		if (branchName.equals(path)) {
			cont.receiveException(new RuntimeException("Branch name and path must be different!"));
		} else {
			wimpyStoreFile(P2PMudFile.create(idFactory.buildId(branchName), branchName, base, path), cont);
		}
	}
	public void wimpyStoreFile(final P2PMudFile file, final Continuation cont) {
		System.out.println("WIMPY STORE FILE: " + file.getId().toStringFull());
		if (file != null) {
			past.insert(file, new Continuation() {
				public void receiveResult(final Object result) {          
					final P2PMudFilePath filePath = new P2PMudFilePath(idFactory.buildId(file.branch), file.path, file.getId());
					Boolean[] results = ((Boolean[]) result);
					int numSuccessfulStores = 0;

					for (int ctr = 0; ctr < results.length; ctr++) {
						if (results[ctr].booleanValue()) 
							numSuccessfulStores++;
					}
					System.out.println(file + " successfully stored at " + numSuccessfulStores + " locations.");
					past.insert(filePath, new Continuation() {
						public void receiveResult(Object result) {        
							Boolean[] results = ((Boolean[]) result);
							int numSuccessfulStores = 0;
							for (int ctr = 0; ctr < results.length; ctr++) {
								if (results[ctr].booleanValue()) 
									numSuccessfulStores++;
							}
							System.out.println(filePath + " successfully stored at " + numSuccessfulStores + " locations.");
							cont.receiveResult(file);
						}
						public void receiveException(Exception exception) {
							cont.receiveException(exception);
						}
					});
				}
				public void receiveException(Exception result) {
					cont.receiveException(result);
				}
			});
		} else {
			cont.receiveResult(null);
		}
	}
	public void wimpyGetFile(Id id, File base, Continuation handler) {
		System.out.println("LOOKING UP: " + id.toStringFull());
		past.lookup(id, handler);
	}
}
