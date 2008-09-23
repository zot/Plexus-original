package p2pmud;

import java.io.File;
import java.io.FilePermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import sun.security.util.SecurityConstants;
import javolution.util.FastSet;

public class PlexusSecurityManager extends SecurityManager {
	private boolean allowAll = false;
	private Object key = new Object();
	private FastSet<Thread> authorizedThreads = new FastSet<Thread>();
	private FastSet<Permission> permissions = new FastSet<Permission>(new HashSet<Permission>(Arrays.asList(allowed)));

	private static final String filePath;
	static {
		URI clUrl;
		String val = null;
		try {
			clUrl = PlexusSecurityManager.class.getResource("/Props.class").toURI();
			if (clUrl.getScheme().equals("file")) {
				val = new File(clUrl).getParent();
				System.out.println("PLEXUS PATH: " + val);
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		filePath = val;
	}
	private static final Permission allowed[] = {
		SecurityConstants.CHECK_MEMBER_ACCESS_PERMISSION
	};
	private static final PlexusSecurityManager soleInstance = new PlexusSecurityManager();

	public static void main(String[] args) {
		install();
		uninstall(soleInstance.key);
	}
	public static Object install() {
		soleInstance.allowAll = true;
		System.setSecurityManager(soleInstance);
		soleInstance.allowAll = false;
		return soleInstance.key;
	}
	public static void uninstall(Object k) {
		soleInstance._uninstall(k);
	}
	public static Object getKey() {
		if (System.getSecurityManager() != null) {
			throw new AccessControlException("Access denied attempting to retrieve key for PLEXUS security manager");
		}
		return soleInstance.key;
	}
	public static void runSandboxed(Runnable block) {
		if (System.getSecurityManager() != null) {
			block.run();
		} else {
			install();
			block.run();
			uninstall(soleInstance.key);
		}
	}
	public static void runAuthorized(Object key, Runnable block) {
		if (System.getSecurityManager() == null) {
			block.run();
		} else {
			boolean oldAllow = soleInstance.allowAll;

			soleInstance.allowAll = true;
			block.run();
			soleInstance.allowAll = oldAllow;
		}
	}
	public static void checkPermission(String perm) {
		SecurityManager sm = System.getSecurityManager();

		if (sm != null) {
			sm.checkPermission(new RuntimePermission(perm));
		}
	}

	public void authorize(Thread t) {
		if (isAuthorized()) {
			authorizedThreads.add(t);
		} else {
			throw new AccessControlException("Access denied attempting to authorize thread with PLEXUS security manager");
		}
	}
	public void _uninstall(Object k) {
		if (k == key) {
			allowAll = true;
			System.setSecurityManager(null);
		} else {
			throw new AccessControlException("Access denied attempting to uninstall PLEXUS security manager");
		}
	}
	public boolean isAuthorized() {
		return allowAll || authorizedThreads.contains(Thread.currentThread());
	}
	public boolean isOk(Permission perm) {
		if (permissions.contains(perm)) {
			return true;
		}
		if (perm instanceof FilePermission) {
			FilePermission p = (FilePermission) perm;

			if (p.getActions().equals("read") && p.getName().startsWith(filePath)) {
				return true;
			}
		}
		return false;
	}
	public void checkPermission(Permission perm) {
		if (!isAuthorized() && !isOk(perm)) {
//			new Exception("BORK: " + perm).printStackTrace();
			super.checkPermission(perm);
		}
	}
}
