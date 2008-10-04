package p2pmud;

import groovy.lang.Closure;

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
import rice.environment.logging.Logger;
import rice.environment.logging.file.RotatingLogManager;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.time.simple.SimpleTimeSource;
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
import rice.pastry.NetworkListener;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.persistence.LRUCache;
import rice.persistence.MemoryStorage;
import rice.persistence.PersistentStorage;
import rice.persistence.StorageManagerImpl;
import rice.selector.SelectorManager;


public class P2PMudPeer implements Application, ScribeMultiClient {
	public PastryNode node;
	private Endpoint endpoint;
	private ScribeImpl myScribe;
	private Environment env;
	private PastImpl past;
	private PastryIdFactory idFactory;
	private InetAddress outgoingAddress;
	private Id other;
	private String joinFailedReason;
	public rice.pastry.Id nodeId;
	public FastMap<Topic, Continuation<Topic, Exception>> subscriptionContinuations = new FastMap<Topic, Continuation<Topic,Exception>>();

	private static int mode;
	private static P2PMudCommandHandler cmdHandler;
	private static String nodeIdString;
	private static Runnable neighborChange;
	public static int chunkBatch = 10;

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
	public static boolean verboseLogging = false;
	public static File logFile = null;
	public static File sauerLogFile = null;
	
	public static void main(P2PMudCommandHandler handler, Runnable neighborChangeBlock, String args[]) throws Exception {
		cmdHandler = handler;
		neighborChange = neighborChangeBlock;
		main(args);
	}
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
												Tools.stackTrace(exception);
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
//												Tools.stackTrace(exception);
//											}
//										});
										break;
									case PAST_GET:
										File base = new File(new File(System.getProperty("sauerdir")), "/packages/p2pmud");

										Thread.sleep(5000);
										test.wimpyGetFile(rice.pastry.Id.build(savedCmds.get(i)), base, new Closure(this) {}, new Continuation<ArrayList<?>, Exception>() {
											public void receiveResult(ArrayList<?> result) {
												if (((Collection)result.get(1)).isEmpty()) {
													System.out.println("Loaded file: " + result.get(0));
												} else {
													System.out.println("Could not load data for file: " + result.get(0) + ", missing ids: " + result.get(1));
												}
											}
											public void receiveException(Exception exception) {
												Tools.stackTrace(exception);
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
												Tools.stackTrace(exception);
											}
										});
										break;
									}
									case FETCH: {
										final String cacheDir = savedCmds.get(i);
										final String dstDir = savedCmds.get(++i);
										String id = savedCmds.get(++i);

										test.wimpyGetFile(rice.pastry.Id.build(id), new File(cacheDir), new Closure(this) {}, new Continuation<ArrayList<?>, Exception>() {
											public void receiveResult(ArrayList<?> result) {
												P2PMudFile.fetchDirFromProperties(cacheDir, result.get(3), Tools.properties(result.get(0)), dstDir, 0, new Closure(this) {}, new Continuation() {
													public void receiveResult(Object result) {
														System.out.println("STORED");
													}
													public void receiveException(Exception exception) {
														Tools.stackTrace(exception);
													}
												}, false);
											};
											public void receiveException(Exception exception) {
												Tools.stackTrace(exception);
											};
										});
										break;
									}
									}
								}
							}
						} catch (Exception e) {
							Tools.stackTrace(e);
						}
					}
				}.start();
			}
		} catch (Exception e) {
			throw e; 
		}
	}

	public void destroy() {
		if (node != null) {
			node.destroy();
			node = null;
		}
		if (myScribe != null) {
			myScribe.destroy();
			myScribe = null;
		}
	}
	public String routeState() {
		return node != null ? node.printRouteState() : "null";
	}
	public Id buildId(String path) {
		return idFactory.buildId(path);
	}
	public Id randomId() {
		return idFactory.buildRandomId(env.getRandomSource());
	}
	/**
	 * This constructor sets up a PastryNode.  It will bootstrap to an 
	 * existing ring if it can find one at the specified location, otherwise
	 * it will start a new ring.
	 * 
	 * @param bindport the local port to bind to 
	 * @param bootaddress the IP:port of the node to boot from
	 */
	public void connect(String args[], int bindport, InetSocketAddress bootaddress) throws Exception {
		Parameters params = new SimpleParameters(Environment.defaultParamFileArray, null);
		SimpleTimeSource timeSrc = new SimpleTimeSource();
		RotatingLogManager logMgr = null;
		SelectorManager selectorMgr = null;

		// disable the UPnP setting (in case you are testing this on a NATted LAN)
		params.setInt("p2p_past_messageTimeout", Integer.parseInt(System.getProperty("past.timeout", "30000")));
		params.setString("nat_search_policy", "never");
		params.setString("probe_for_external_address", "true");
		params.setString("pastry_socket_writer_max_msg_size", "65536");
		params.setString("pastry_socket_writer_max_queue_length", "200");
		if (verboseLogging) {
			params.setInt("loglevel", Logger.FINE);
			if (!logFile.getParentFile().exists()) {
				logFile.getParentFile().mkdirs();
			}
			params.setString("log_rotate_filename", logFile.getAbsolutePath());
			logMgr = new RotatingLogManager(timeSrc, params);
			selectorMgr = Environment.generateDefaultSelectorManager(timeSrc, logMgr);
			logMgr.startRotateTask(selectorMgr);
		}
		env = new Environment(selectorMgr, null, null, timeSrc, logMgr, params, null);
		NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
		FastTable<InetSocketAddress> probes = new FastTable<InetSocketAddress>();
		if (probeHost != null) {
			env.getParameters().setString("external_address", probeHost + ":" + probePort);
			probes.add(new InetSocketAddress(probeHost, probePort));
		}
		SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, outgoingAddress, bindport, env);
		nodeId = nodeIdString != null ? rice.pastry.Id.build(nodeIdString) : nidFactory.generateNodeId();
		System.out.println("Using boot address: " + bootaddress);
		boolean success = false;
		for (int i = 0; i < 1 && !success; i++) {
			if (i > 0) System.out.println("TRYING AGAIN TO JOIN RING.  ATTEMPT NUMBER " + (i + 1));
			success = attemptConnect(bootaddress, probes, factory);
		}
		if (!success) {
			throw new IOException("Could not join the FreePastry ring.  Reason:" +  joinFailedReason);
		}
		System.out.println("Finished creating new node " + node + ", count: " + node.getLeafSet().getUniqueCount());
		startPast();
	}
	protected boolean attemptConnect(InetSocketAddress bootaddress, FastTable<InetSocketAddress> probes, SocketPastryNodeFactory factory) throws InterruptedException, IOException {
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
			int countdown = 1200;
			int oldNeighborCount = 1;
			while (!node.isReady() && !node.joinFailed()) {
				// delay so we don't busy-wait
				node.wait(500);
				int cnt = getNeighborCount();
				if (cnt > oldNeighborCount) {
					countdown += 120;
					oldNeighborCount = cnt;
					System.out.println("New neighbor count is " + cnt);
				}
				// abort if can't join
				if (node.joinFailed() || --countdown < 0) {
					joinFailedReason = countdown < 0 ? "timeout retries exceeded" : node.joinFailedReason().toString();
					destroy();
					return false;
				}
				System.out.println("Waiting on node again, roughly " + (countdown / 2.0) + " seconds left in this attempt");				
			}
		}
		return true;
	}
	private void getOther() {
		if (other == null) {
			if (node.getLeafSet().iterator().hasNext()) {
				other = node.getLeafSet().iterator().next().getId();
			}
		}
	}
	public Collection<Topic> getTopics() {
		return myScribe != null ? myScribe.getTopicsByClient(this) : new ArrayList();
	}
	public Topic subscribe(Id topicId, Continuation<Topic, Exception> cont) {
		Topic topic = new Topic(topicId);

		if (cont != null) {
			synchronized (subscriptionContinuations) {
				subscriptionContinuations.put(topic, cont);
			}
		}
		System.out.println("SUBSCRIBING TO: " + topic);
		myScribe.subscribe(topic, this);
		return topic;
	}
	public void unsubscribe(Topic topic) {
		System.out.println("UNSUBSCRIBING FROM: " + topic);
		myScribe.unsubscribe(topic, this);
	}
	private void startPast() throws IOException {
		// create a different storage root for each node
		String storageDirectory = System.getProperty("past.storage", "/tmp/storage"+node.getId().hashCode());
	
		// create the persistent part
		PersistentStorage stor = new PersistentStorage(idFactory, storageDirectory, 4 * 1024 * 1024, node.getEnvironment());
		stor.setStorageSize(Integer.parseInt(System.getProperty("past.storagesize", String.valueOf(1024 * 1024 * 300))));
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
		if (!joined) cmdHandler.handleCommand(handle.getId(), null, null);
		if (neighborChange != null) {
			neighborChange.run();
		}
	}
	public int getNeighborCount() {
		return node != null ? node.getLeafSet().neighborSet(node.getLeafSet().maxSize()).size() : 0;
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
int count;
	public void wimpyStoreFile(final File cacheDir, final Object filename, final Closure callback, final Continuation<P2PMudFile, Exception> cont, boolean mutable, final boolean cacheOverride) {
		final ArrayList<PastContent> chunks = P2PMudFile.create(cacheDir, filename, mutable, cacheOverride);

		if (!cacheOverride && chunks.size() == 1) {
			System.out.println("FILE ALREADY CACHED: " + (filename instanceof byte[] ? "<data>" : filename) + " because it is already cached");
			for (int i = 0; i < ((P2PMudFile) chunks.get(0)).chunks.size(); i++) {
				callback.call();
			}
			cont.receiveResult((P2PMudFile) chunks.get(0));
		} else {
			System.out.println("STORING FILE: " + chunks.get(0) + (cacheOverride ? " [overriding cache]" : ""));
count = 0;
			storeChunks((P2PMudFile)chunks.get(0), callback, new Continuation<P2PMudFile, Exception>() {
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
					Tools.stackTrace(exception);
					cont.receiveException(exception);
				}
			}, chunks, chunkBatch);
		}
	}
	protected void storeChunks(final P2PMudFile mudFile, final Closure callback, final Continuation<P2PMudFile, Exception> cont, final ArrayList<PastContent> chunks, final int attempts) {
		int contCount = 0;
		int stored = Math.min(chunkBatch, chunks.size());
		MultiContinuation multi = new MultiContinuation(new Continuation<Object[], Exception>() {
			public void receiveResult(Object[] result) {
				int failureCount = 0;
				
				for (int i = result.length; i-- > 0; ) {
					if (result[i] instanceof Exception) {
						failureCount++;
					} else {
						chunks.remove(i);
					}
				}
				if (chunks.isEmpty()) {
					cont.receiveResult(mudFile);
				} else if (attempts == 0) {
					cont.receiveException(new RuntimeException("Timed out storing chunks: " + chunks));
				} else {
					storeChunks(mudFile, callback, cont, chunks, failureCount == result.length ? attempts - 1 : chunkBatch);
				}
			}
			public void receiveException(Exception exception) {
				cont.receiveException(exception);
			}
		}, stored);

		for (PastContent content : chunks) {
			final Continuation<Object, Exception> sub = multi.getSubContinuation(contCount++);

			if (content instanceof P2PMudFileChunk) {
				System.out.println("INSERTING CHUNK: " + ((P2PMudFileChunk)content).offset);
			}
final int c = count++;
			past.insert(content, new Continuation.StandardContinuation(sub) {
				public void receiveResult(Object result) {
					System.out.println("CALLBACK #" + c);
					callback.call();
					sub.receiveResult(result);
				}
			});
			if (contCount >= stored) {
				break;
			}
		}
	}
	/**
	 * Sends {filename, missingChunks, P2PMudFile, id} to handler
	 */
	public void wimpyGetFile(final Id id, final File cacheDir, final Closure callback, final Continuation cont) {
		System.out.println("LOOKING UP: " + id.toStringFull());
		cacheDir.mkdirs();
		if (P2PMudFile.filename(cacheDir, id).exists()) {
			ArrayList l = new ArrayList();
			
			l.add(P2PMudFile.filename(cacheDir, id));
			l.add(null);
			l.add(null);
			l.add(id);
			cont.receiveResult(l);
		} else {
			past.lookup(id, new Continuation<P2PMudFile, Exception>() {
				public void receiveResult(final P2PMudFile file) {
					if (file != null) {
						String data[] = new String[file.chunks.size()];

						System.out.println("RETRIEVED FILE OBJECT: " + file + ", getting chunks");
						getChunks(cacheDir, callback, cont, file, file.chunks, data, 0);
					} else {
						cont.receiveException(new FileNotFoundException("No file found for " + id.toStringFull()));
					}
				}
				public void receiveException(Exception exception) {
					cont.receiveException(exception);
				}
			});
		}
	}
	protected void getChunks(final File cacheDir, final Closure callback, final Continuation handler, final P2PMudFile file, final ArrayList<Id> chunks, final String data[], final int attempt) {
		if (attempt > chunkBatch) {
			//maybe pass the missing chunks in this exception
			handler.receiveException(new RuntimeException("Made " + attempt + " attempts to get file without receiving any new data"));
			return;
		}
		final ArrayList<Id> missing = new ArrayList<Id>();
		int count = Math.min(chunks.size(), chunkBatch);
		
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
						StringBuffer buf = new StringBuffer();
						ArrayList value = new ArrayList();

						for (int i = 0; i < data.length; i++) {
							buf.append(data[i]);
						}
						byte[] bytes = Tools.decode(buf.toString());
						output.write(bytes, 0, file.size);
						output.flush();
						output.getFD().sync();
						output.close();
						value.add(fn);
						value.add(missing.isEmpty() ? null : missing);
						value.add(file);
						value.add(file.getId());
						handler.receiveResult(value);
					} else {
						getChunks(cacheDir, callback, handler, file, missing, data, !any ? attempt + 1 : 0);
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
				final Continuation<Object, Exception> sub = cont.getSubContinuation(i);

				System.out.println("LOOKING UP CHUNK: " + chunks.get(i).toStringFull());
				past.lookup(chunks.get(i), new Continuation.StandardContinuation(sub) {
					public void receiveResult(Object result) {
						callback.call(file.chunks.size());
						sub.receiveResult(result);
					}
				});
			} else {
				missing.add(chunks.get(i));
			}
		}
	}
}
