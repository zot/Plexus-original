package p2pmud;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

import javolution.util.FastMap;
import javolution.util.FastTable;
import rice.Continuation;
import rice.Continuation.MultiContinuation;
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


public class P2PMudPeer implements Application, ScribeMultiClient {
	public PastryNode node;
	private Endpoint endpoint;
	private ScribeImpl myScribe;
	private Environment env;
	private PastImpl past;
	private PastryIdFactory idFactory;
	private InetAddress outgoingAddress;
	private Id other;
	public rice.pastry.Id nodeId;
	public FastMap<Topic, Continuation<Topic, Exception>> subscriptionContinuations = new FastMap<Topic, Continuation<Topic,Exception>>();

	private static int mode;
	private static P2PMudCommandHandler cmdHandler;
	private static String nodeIdString;
	private static Runnable neighborChange;

	private static final P2PMudPeer test = new P2PMudPeer();
	private static final int NONE = -1;
	private static final int SCRIBE = 0;
	private static final int PAST = 1;
	private static final int PAST_GET = 2;
	private static final int CMD = 3;
	private static final int STORE = 4;
	private static final int FETCH = 5;
	private static ArrayList<String> savedCmds = new ArrayList<String>();
	private static String probeHost;
	private static int probePort;
	public static String node_interface;
	
	public static void main(P2PMudCommandHandler handler, Runnable neighborChangeBlock, String args[]) throws Exception {
		cmdHandler = handler;
		neighborChange = neighborChangeBlock;
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

			if (node_interface != null && node_interface != "") {
				test.outgoingAddress = InetAddress.getByName(node_interface);
				System.out.println("Using node_interface: " + test.outgoingAddress);
				bootaddr = test.outgoingAddress;
			} else {
				faces: for (Enumeration interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
					NetworkInterface face = (NetworkInterface) interfaces.nextElement();
					
					for(Enumeration addrs = face.getInetAddresses(); addrs.hasMoreElements(); ) {
						InetAddress addr = (InetAddress) addrs.nextElement();
						
						if (addr.isAnyLocalAddress()) {
							break;
						} else if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
							test.outgoingAddress = addr;
							break faces;
						}
					}
				}
				System.out.println("Using interface: " + test.outgoingAddress);
				bootaddr = host.equals("-") ? test.outgoingAddress : InetAddress.getByName(host);
			}
			if (bootaddr == null) {
				throw new RuntimeException("Could not find interface");
			}
			int bootport = Integer.parseInt(args[2]);
			InetSocketAddress bootaddress = host.equals("-") ? null : new InetSocketAddress(bootaddr,bootport);
			for (int i = 3; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("-clean") && System.getProperty("past.storage") != null) {
					File storage = new File(System.getProperty("past.storage"));

					if (storage.exists()) {
						System.out.println("CLEANING STORAGE");
						Tools.deleteAll(storage);
					}
				} else if (args[i].equalsIgnoreCase("-nodeid")) {
					nodeIdString = args[++i];
				} else if (args[i].equalsIgnoreCase("-external")) {
					int col = args[++i].indexOf(':');

					probeHost = args[i].substring(0, col);
					probePort = Integer.parseInt(args[i].substring(col + 1));
				} else {
					savedCmds.add(args[i]);
				}
			}
			// launch our node!
			test.connect(args, bindport, bootaddress);
			if (!savedCmds.isEmpty()) {
				new Thread() {
					public void run() {
						try {
							Thread.sleep(5000);
							mode = NONE;
							for (int i = 0; i < savedCmds.size(); i++) {
								if (savedCmds.get(i).equalsIgnoreCase("-scribe")) {
									mode = SCRIBE;
								} else if (savedCmds.get(i).equalsIgnoreCase("-past")) {
									mode = PAST;
								} else if (savedCmds.get(i).equalsIgnoreCase("-pastget")) {
									mode = PAST_GET;
								} else if (savedCmds.get(i).equalsIgnoreCase("-cmd")) {
									mode = CMD;
								} else if (savedCmds.get(i).equalsIgnoreCase("-store")) {
									mode = STORE;
								} else if (savedCmds.get(i).equalsIgnoreCase("-fetch")) {
									mode = FETCH;
								} else if (savedCmds.get(i).charAt(0) != '-') {
									switch (mode) {
									case SCRIBE: {
										final String cmd = savedCmds.get(i);

										test.subscribe(test.buildId("updates for bubba"), new Continuation<Topic, Exception>() {
											public void receiveResult(Topic topic) {
												test.broadcastCmds(topic, new String[]{cmd});
											}
											public void receiveException(Exception exception) {
												System.out.println("Couldn't subscribe to topic");
											}
										});
										break;
									}
									case PAST:
//										test.wimpyStoreFile("map.ogz", new File(System.getProperty("sauerdir")), savedCmds.get(i), new Continuation<P2PMudFile, Exception>() {
//											public void receiveResult(P2PMudFile result) {
//												if (result != null) {
//													System.out.println("Stored file: " + result);
//												} else {
//													System.out.println("Couldn't store file: map");
//												}
//											}
//											public void receiveException(Exception exception) {
//												System.err.println("Error while attempting to store file: branch");
//												exception.printStackTrace();
//											}
//										});
										break;
									case PAST_GET:
										File base = new File(new File(System.getProperty("sauerdir")), "/packages/p2pmud");

										Thread.sleep(5000);
										test.wimpyGetFile(rice.pastry.Id.build(savedCmds.get(i)), base, new Continuation<Object[], Exception>() {
											public void receiveResult(Object[] result) {
												if (((Collection)result[1]).isEmpty()) {
													System.out.println("Loaded file: " + result[0]);
												} else {
													System.out.println("Could not load data for file: " + result[0] + ", missing ids: " + result[1]);
												}
											}
											public void receiveException(Exception exception) {
												exception.printStackTrace();
											}
										});
										break;
									case CMD:
										cmdHandler = new P2PMudCommandHandler() {
											public void handleCommand(Id forPeer, Topic topic, P2PMudCommand cmd) {
												System.out.println("Received " + cmd.msgs.length + " commands...");
												for (String m : cmd.msgs) {
													System.out.println(m);
												}
											}
										};
										test.sendCmds(new String[]{savedCmds.get(i)});
										break;
									case STORE: {
										String cacheDir = savedCmds.get(i);
										String srcDir = savedCmds.get(++i);

										System.out.println("STORING DIR: " + srcDir + ", cache: " + cacheDir);
										P2PMudFile.storeDir(cacheDir, srcDir, new Continuation() {
											public void receiveResult(Object result) {
												System.out.println("Stored dir: " + result);
											}
											public void receiveException(Exception exception) {
												exception.printStackTrace();
											}
										});
										break;
									}
									case FETCH: {
										final String cacheDir = savedCmds.get(i);
										final String dstDir = savedCmds.get(++i);
										String id = savedCmds.get(++i);

										test.wimpyGetFile(rice.pastry.Id.build(id), new File(cacheDir), new Continuation<Object[], Exception>() {
											public void receiveResult(Object result[]) {
												P2PMudFile.fetchDirFromProperties(cacheDir, result[3], Tools.properties(result[0]), dstDir, new Continuation() {
													public void receiveResult(Object result) {
														System.out.println("STORED");
													}
													public void receiveException(Exception exception) {
														exception.printStackTrace();
													}
												}, false);
											};
											public void receiveException(Exception exception) {
												exception.printStackTrace();
											};
										});
										break;
									}
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
		env.getParameters().setInt("p2p_past_messageTimeout", Integer.parseInt(System.getProperty("past.timeout", "15000")));
		env.getParameters().setString("nat_search_policy", "never");
		env.getParameters().setString("probe_for_external_address", "true");
		NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
		FastTable<InetSocketAddress> probes = new FastTable<InetSocketAddress>();
		if (probeHost != null) {
			env.getParameters().setString("external_address", probeHost + ":" + probePort);
			probes.add(new InetSocketAddress(probeHost, probePort));
		}
		SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, outgoingAddress, bindport, env);
		nodeId = nodeIdString != null ? rice.pastry.Id.build(nodeIdString) : nidFactory.generateNodeId();
		if (probes.isEmpty()) {
			node = factory.newNode(nodeId);
		} else {
			node = factory.newNode(nodeId, probes.get(0));
		}
		node.boot(bootaddress);
		idFactory = new PastryIdFactory(node.getEnvironment());
		endpoint = node.buildEndpoint(this, "myinstance");
		endpoint.register();
		myScribe = new ScribeImpl(node,"myScribeInstance");
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
		startPast();
	}
	private void getOther() {
		if (other == null) {
			if (node.getLeafSet().iterator().hasNext()) {
				other = node.getLeafSet().iterator().next().getId();
			}
		}
	}
	public Topic subscribe(Id topicId, Continuation<Topic, Exception> cont) {
		Topic topic = new Topic(topicId);

		if (cont != null) {
			synchronized (subscriptionContinuations) {
				subscriptionContinuations.put(topic, cont);
			}
		}
		myScribe.subscribe(topic, this);
		return topic;
	}
	public void unsubscribe(Topic topic) {
		myScribe.unsubscribe(topic, this);
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
			handleCommand(id, null, ((P2PMudMessage)message).cmd);
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
		if (neighborChange != null) {
			neighborChange.run();
		}
	}
	public int getNeighborCount() {
		return node.getLeafSet().neighborSet(node.getLeafSet().maxSize()).size();
	}
	public boolean anycast(Topic topic, ScribeContent content) {
		if (content instanceof P2PMudScribeContent) {
			handleCommand(null, topic, ((P2PMudScribeContent)content).cmd);
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
			handleCommand(null, topic, ((P2PMudScribeContent)content).cmd);
		} else {
			System.out.println("Received update: " + content);
		}
	}
	protected void handleCommand(Id id, Topic topic, P2PMudCommand mudCommand) {
		if (cmdHandler != null && !mudCommand.from.equals(node.getId())) {
			other = mudCommand.from;
			cmdHandler.handleCommand(id, topic, mudCommand);
		}
	}
	public void subscribeFailed(Collection<Topic> topics) {
		for (Topic topic: topics) {
			subscribeFailed(topic);
		}
	}
	public void subscribeFailed(Topic topic) {
		Continuation<Topic, Exception> cont;
		
		synchronized (subscriptionContinuations) {
			cont = subscriptionContinuations.remove(topic);
		}
		if (cont != null) {
			cont.receiveException(new Exception("Failed to subscribe to topic: " + topic));
		}
	}
	public void subscribeSuccess(Collection<Topic> topics) {
		for (Topic topic: topics) {
			subscribeSuccess(topic);
		}
	}
	public void subscribeSuccess(Topic topic) {
		Continuation<Topic, Exception> cont;
		
		synchronized (subscriptionContinuations) {
			cont = subscriptionContinuations.remove(topic);
		}
		if (cont != null) {
			cont.receiveResult(topic);
		}
	}
	public void anycastCmds(Topic myTopic, String cmds[]) {
		if (myTopic != null) {
			myScribe.anycast(myTopic, new P2PMudScribeContent(new P2PMudCommand(node.getId(), cmds)));
		}
	}
	public void broadcastCmds(Topic myTopic, String cmds[]) {
		if (myTopic != null) {
			myScribe.publish(myTopic, new P2PMudScribeContent(new P2PMudCommand(node.getId(), cmds)));
		}
	}
	public void sendCmds(String cmds[]) {
		getOther();
		if (other != null) {
			sendCmds(other, cmds);
		}
	}
	public void sendCmds(Id id, String cmds[]) {
		sendCmds(id, new P2PMudCommand(nodeId, cmds));
	}
	public void sendCmds(Id id, P2PMudCommand cmd) {
		endpoint.route(id, new P2PMudMessage(cmd), null);
	}
	/**
	 * sends the P2PMudFile to cont
	 */
	public void wimpyStoreFile(final File cacheDir, final Object filename, final Continuation<P2PMudFile, Exception> cont, boolean mutable, final boolean cacheOverride) {
		final ArrayList<PastContent> chunks = P2PMudFile.create(cacheDir, filename, mutable, cacheOverride);

		if (!cacheOverride && chunks.size() == 1) {
			System.out.println("FILE ALREADY CACHED: " + (filename instanceof byte[] ? "<data>" : filename) + " because it is already cached");
			cont.receiveResult((P2PMudFile) chunks.get(0));
		} else {
			System.out.println("STORING FILE: " + chunks.get(0) + (cacheOverride ? " [overriding cache]" : ""));
			storeChunks((P2PMudFile)chunks.get(0), new Continuation<P2PMudFile, Exception>() {
				public void receiveResult(P2PMudFile result) {
					File dest = result.filename(cacheDir);

					System.out.println("SUCCEEDED STORING FILE: " + result);
					dest.getParentFile().mkdirs();
					if (!dest.exists()) {
						Tools.copyFile(filename, dest);
					}
					cont.receiveResult(result);
				}
				public void receiveException(Exception exception) {
					System.out.println("FAILED TO STORE FILE: " + chunks.get(0));
					exception.printStackTrace();
					cont.receiveException(exception);
				}
			}, chunks, 5);
		}
	}
	protected void storeChunks(final P2PMudFile mudFile, final Continuation<P2PMudFile, Exception> cont, final ArrayList<PastContent> chunks, final int attempts) {
		final ArrayList<PastContent> failed = new ArrayList<PastContent>();
		MultiContinuation multi = new MultiContinuation(new Continuation<Object[], Exception>() {
			public void receiveResult(Object[] result) {
				for (int i = 0; i < result.length; i++) {
					if (result[i] instanceof Exception) {
						failed.add(chunks.get(i));
					}
				}
				if (failed.isEmpty()) {
					cont.receiveResult(mudFile);
				} else if (attempts == 0) {
					cont.receiveException(new RuntimeException("Failed to store chunks"));
				} else {
					storeChunks(mudFile, cont, failed, attempts - 1);
				}
			}
			public void receiveException(Exception exception) {
				cont.receiveException(exception);
			}
		}, chunks.size());

		for (int i = 0; i < chunks.size(); i++) {
			System.out.println("INSERTING CHUNK: " + chunks.get(i));
			past.insert(chunks.get(i), multi.getSubContinuation(i));
		}
	}
	/**
	 * Sends {filename, missingChunks, P2PMudFile, id} to handler
	 */
	public void wimpyGetFile(final Id id, final File cacheDir, final Continuation handler) {
		System.out.println("LOOKING UP: " + id.toStringFull());
		cacheDir.mkdirs();
		if (P2PMudFile.filename(cacheDir, id).exists()) {
			handler.receiveResult(new Object[]{P2PMudFile.filename(cacheDir, id), null, null, id});
		} else {
			past.lookup(id, new Continuation<P2PMudFile, Exception>() {
				public void receiveResult(final P2PMudFile file) {
					if (file != null) {
						String data[] = new String[file.chunks.size()];

						System.out.println("RETRIEVED FILE OBJECT: " + file + ", getting chunks");
						getChunks(cacheDir, handler, file, file.chunks, data, 0);
					} else {
						handler.receiveException(new FileNotFoundException("No file found for " + id.toStringFull()));
					}
				}
				public void receiveException(Exception exception) {
					handler.receiveException(exception);
				}
			});
		}
	}
	protected void getChunks(final File cacheDir, final Continuation handler, final P2PMudFile file, final ArrayList<Id> chunks, final String data[], final int attempt) {
		if (attempt > 5) {
			//maybe pass the missing chunks in this exception
			handler.receiveException(new RuntimeException("Made " + attempt + " attempts to get file without receiving any new data"));
			return;
		}
		final ArrayList<Id> missing = new ArrayList<Id>();
		int count = Math.min(chunks.size(), 5);
		
		System.out.println("GETTING " + count + " CHUNKS...");
		final MultiContinuation cont = new MultiContinuation(new Continuation<Object[], Exception>() {
			public void receiveResult(Object result[]) {
				try {
					boolean any = false;

					for (int i = 0; i < result.length; i++) {
						if (result[i] instanceof Exception || result[i] == null) {
							missing.add(chunks.get(i));
						} else {
							P2PMudFileChunk chunk = (P2PMudFileChunk)result[i];

							data[chunk.offset] = chunk.data;
							any = true;
						}
					}
					if (missing.isEmpty()) {
						File fn = file.filename(cacheDir);
						fn.getParentFile().mkdirs();
						FileOutputStream output = new FileOutputStream(fn);
						Object value[] = {fn, missing.isEmpty() ? null : missing, file, file.getId()};
						StringBuffer buf = new StringBuffer();

						for (int i = 0; i < data.length; i++) {
							buf.append(data[i]);
						}
						byte[] bytes = Tools.decode(buf.toString());
						output.write(bytes, 0, file.size);
						output.flush();
						output.getFD().sync();
						output.close();
						handler.receiveResult(value);
					} else {
						getChunks(cacheDir, handler, file, missing, data, !any ? attempt + 1 : 0);
					}
				} catch (Exception ex) {
					handler.receiveException(ex);
				}
			}
			public void receiveException(Exception exception) {
				handler.receiveException(exception);
			}
		}, count);

		for (int i = 0; i < chunks.size(); i++) {
			if (i < count) {
				System.out.println("LOOKING UP CHUNK: " + chunks.get(i).toStringFull());
				past.lookup(chunks.get(i), cont.getSubContinuation(i));
			} else {
				missing.add(chunks.get(i));
			}
		}
	}
}
