#include "pch.h"
#include "engine.h"
#include <fcntl.h>
#include <errno.h>
#include "remote.h"
#include "tc.h"

#define UNSET 0
#define SET 1
#define NOT_ALLOWED 2

static int initialized = UNSET;
static int remotePort = -1;
static char *remoteHost;
static ENetSocket mysocket = -1;
static long totalBytesRead = 0L, totalBytesWritten = 0L;
static vector<char> input;
static vector<char> output;

static char *remotedisconnect(char *msg) {
	if (mysocket != -1) {
		enet_socket_destroy(mysocket); //close(mysocket);
		mysocket = -1;
		conoutf("Disconnected from remote host: %s", msg ? msg : "");
	}
	output.setsize(0);
	input.setsize(0);
	perror(msg ? msg : "");
	totalBytesRead = totalBytesWritten = 0L;
	return NULL;
}
	//extern int remotePort;
	//extern char *remoteHost;
	//extern ENetSocket mysocket;
	//extern long totalBytesRead, totalBytesWritten;

ICOMMAND(remotestatus, "", (), {
	char buf[512];
	snprintf(buf, sizeof(buf), "hostname %s port %d socket %d read %ld written %ld", remoteHost, remotePort, mysocket, totalBytesRead, totalBytesWritten);
	result(buf);
});

ICOMMAND(remotedisconnect, "s", (char *msg), remotedisconnect(msg));

ICOMMAND(remotedisable, "", (),
	if (initialized == SET) {
		remotedisconnect("remote connections disabled");
		delete[] remoteHost;
		enet_deinitialize();
	}
	initialized = NOT_ALLOWED;
);

static char *remoteallow(char *host, int *port) {
	if (host == 0) {
		conoutf("REMOTE ERROR: host is NULL for remote connect");
	} else if (strlen(host) == 0) {
		conoutf("REMOTE ERROR: host is empty for remote connect");
	} else if (port == 0) {
		conoutf("REMOTE ERROR: No port given for remote connect");
	} else if (*port < 0) {
		conoutf("REMOTE ERROR: Invalid port for remote connect: %d", *port);
	} else {
		switch (initialized) {
		case SET:
			if (strcmp(host, remoteHost)) {
				conoutf("REMOTE SECURITY VIOLATION: attempt to change allowed remote host from: %s to %s.", remoteHost, host);
				break;
			} else if (*port != remotePort) {
				conoutf("REMOTE SECURITY VIOLATION: attempt to change allowed remote port from: %d to %d.", remotePort, port);
				break;
			}
			break;
		case NOT_ALLOWED:
			conoutf("REMOTE SECURITY VIOLATION: attempt to allow remote connections when they have been disabled");
			break;
		case UNSET:
			initialized = SET;
			enet_initialize();
			remoteHost = newstring(host);
			remotePort = *port;
			conoutf("Allowing remote connections to %s:%d", remoteHost, remotePort);
			break;
		}
	}
	return NULL;
}
ICOMMAND(remoteallow, "si", (char *host, int *port), remoteallow(host, port););

static char *remoteconnect() {
	if (mysocket != -1) {
		conoutf("REMOTE ERROR: attempt to connect when already connected.");
		return NULL;
	}
	switch (initialized) {
	case UNSET:
		conoutf("REMOTE ERROR: attempt to connect when host and port are not set");
		break;
	case NOT_ALLOWED:
		conoutf("REMOTE SECURITY VIOLATION: attempt to connect when remote connections have been disabled");
		break;
	case SET:
		static ENetAddress address;
		bool isIPAddr = true;

		address.port = remotePort;
		address.host = 0;
        conoutf("attempting to connect to %s", remoteHost);
        for (char *p = remoteHost; *p; p++) {
        	if ((*p < '0' || '9' < *p) && *p != '.') {
        		isIPAddr = false;
        		break;
        	}
        }
        if (isIPAddr) {
        	printf("USING IP ADDR: %s\n", remoteHost);
        	enet_address_set_host_ip(&address, remoteHost);
        } else {
        	printf("USING HOST NAME: %s\n", remoteHost);
        	if(!resolverwait(remoteHost, &address)) {
        		conoutf("REMOTE ERROR: could not resolve server %s", remoteHost);
        		break;
        	}
        }
		if (-1 == (mysocket = enet_socket_create(ENET_SOCKET_TYPE_STREAM, NULL))) {
			conoutf("REMOTE ERROR: Could not create remote socket");
			break;
		}
		//fprintf(stderr, "Host internal %ld, port %d\n", (long) address.host, address.port);
		if (enet_socket_connect(mysocket, &address)) {
			conoutf("REMOTE ERROR: Could not connect to remote host %s:%d", remoteHost, remotePort);
			mysocket = -1;
			break;
		}
		enet_socket_set_option(mysocket, ENET_SOCKOPT_NONBLOCK, 1);
		conoutf("Connected to %s:%d", remoteHost, remotePort);
		break;
	}
	return NULL;
}
ICOMMAND(remoteconnect, "", (), remoteconnect(););

void tc_remotesend(char *line) {
	//remoteconnect();
	if (mysocket != -1) {
		output.put(line, strlen(line));
		output.add('\n');
	}
}

ICOMMAND(remotesend, "C", (char *line),
		 tc_remotesend(line);
);

static void readChunk() {
	databuf<char> buf = input.reserve(1000);
	ENetBuffer buffer;
	buffer.data = buf.buf;
	buffer.dataLength = 1000;

	int bytesRead = enet_socket_receive(mysocket, NULL, &buffer,1); //recv(mysocket, buf.buf, 1000, MSG_DONTWAIT);
	if (bytesRead < 0 && errno != EAGAIN && bytesRead != EAGAIN) {
		remotedisconnect("connection closed while reading");
	} else if (bytesRead > 0) {
		totalBytesRead += bytesRead;
		int lastNl = -1;
		int oldLen = input.length();

		buf.len = bytesRead;
		input.addbuf(buf);
		for (int i = 0; i < bytesRead; i++) {
			if (buf.buf[i] == '\n') {
				buf.buf[i] = ';';
				lastNl = i;
			}
		}
		if (lastNl > -1) {
			lastNl += oldLen;
			input[lastNl] = 0;
			if (input.length() > 1) {
				char *command = input.getbuf();
				//fprintf(stderr, "Zot (%d):%s\n", lastNl, command);
				executeret(command);
			}
			if (lastNl + 1 < input.length()) {
				input.remove(0, lastNl + 1);
			} else {
				input.setsize(0);
			}
		}
	}
}


static void writeChunk() {
	if (output.length()) {
		int written;

		ENetBuffer buffer;
		buffer.data = output.getbuf();
		buffer.dataLength = output.length();
		written = enet_socket_send(mysocket, NULL, &buffer, 1);
		if (written == EOF) {
			remotedisconnect("connection closed while writing");
		} else {
			totalBytesWritten += written;
			if (written < output.length()) {
				output.remove(0, written);
			} else {
				output.setsize(0);
			}
		}
	}
}

vector<void (*)()> *tickhooks = NULL;

void remotetick() {
	if (-1 != mysocket) {
		writeChunk();
		readChunk();
	}
	if (tickhooks) {
		loopi(tickhooks->length()) {
			((*tickhooks)[i])();
		}
	}
}

void addTickHook(void (*hook)()) {
	if (!tickhooks) {
		tickhooks = new vector<void (*)()>;
	}
	tickhooks->add(hook);
}
