#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#define UNSET 0
#define SET 1
#define NOT_ALLOWED 2

static int initialized = UNSET;
static int remotePort = -1;
static char *remoteHost;
static int mysocket = -1;
static vector<char> input;
static vector<char> output;
static int inputLinesPending = 0;

static char *remotedisconnect(char *msg) {
	if (mysocket != -1) {
		close(mysocket);
		mysocket = -1;
		conoutf("Disconnected from remote host: %s", msg ? msg : "");
		inputLinesPending = 0;
		output.setsize(0);
		input.setsize(0);
		perror(msg ? msg : "");
	}
	return NULL;
}
ICOMMAND(remotedisconnect, "s", (char *msg), remotedisconnect(msg));

ICOMMAND(remotedisable, "", (),
	if (initialized == SET) {
		remotedisconnect("remote connections disabled");
		delete[] remoteHost;
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
		struct sockaddr_in pin;
		struct hostent *hp;

		if ((hp = gethostbyname(remoteHost)) == 0) {
			perror("gethostbyname");
			conoutf("Could not find host: %s", remoteHost);
			break;
		}
		memset(&pin, 0, sizeof(pin));
		pin.sin_family = AF_INET;
		pin.sin_addr.s_addr = ((struct in_addr *)(hp->h_addr))->s_addr;
		pin.sin_port = htons(remotePort);
		if ((mysocket = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
			perror("socket");
			conoutf("Could not create remote socket");
			break;
		}
		if (connect(mysocket, (struct sockaddr *) &pin, sizeof(pin)) == -1) {
			perror("connect");
			conoutf("Could not connect to remote host %s:%d", remoteHost, remotePort);
			mysocket = -1;
			break;
		}
		conoutf("Connected to %s:%d", remoteHost, remotePort);
		break;
	}
	return NULL;
}
ICOMMAND(remoteconnect, "", (), remoteconnect(););

ICOMMAND(remotesend, "V", (char **msgs, int *nummsgs),
	inputLinesPending++;
	for (int i = 0; i < *nummsgs; i++) {
		if (i > 0) {
			output.add(' ');
		}
		output.put(msgs[i], strlen(msgs[i]));
	}
	output.add('\n');
);

static void readChunk() {
	if (inputLinesPending) {
		ssize_t bytesRead;
		databuf<char> buf = input.reserve(1000);

		bytesRead = recv(mysocket, buf.buf, 1000, MSG_DONTWAIT);
		if (bytesRead < 0 && errno != EAGAIN && bytesRead != EAGAIN) {
			remotedisconnect("connection closed while reading");
		} else if (bytesRead > 0) {
			int lastNl = -1;

			buf.len = bytesRead;
			input.addbuf(buf);
			for (int i = 0; i < bytesRead; i++) {
				if (buf.buf[i] == '\n') {
					inputLinesPending--;
					buf.buf[i] = ';';
					lastNl = i;
				}
			}
			if (lastNl > -1) {
				input.last() = 0;
				executeret(input.getbuf());
				if (lastNl + 1 < input.length()) {
					input.remove(0, lastNl + 1);
				} else {
					input.setsize(0);
				}
			}
		}
	}
}

static void writeChunk() {
	if (output.length()) {
		ssize_t written;

		written = send(mysocket, output.getbuf(), output.length(), MSG_DONTWAIT);
		if (written == EOF) {
			remotedisconnect("connection closed while writing");
		} else {
			if (written < output.length()) {
				output.remove(0, written);
			} else {
				output.setsize(0);
			}
		}
	}
}

void remotetick() {
	writeChunk();
	readChunk();
}
