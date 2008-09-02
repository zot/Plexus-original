/*
 * Copyright (c) 2008 TEAM CTHULHU, Bill Burdick, Roy Riggs
 * Plexus is licensed under the ZLIB license (http://www.opensource.org/licenses/zlib-license.php):
 */
#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <string.h>
#include <stdio.h>
#include <stdarg.h>

int writecrosshairs(_IO_FILE*) {return 0;}
int writebinds(_IO_FILE*) {return 0;}
int writecompletions(_IO_FILE*) {return 0;}
void addreleaseaction(char const *duh) {}
void conoutf(char const *format, ...) {
	va_list ap;

	va_start(ap, format);
	vprintf(format, ap);
	va_end(ap);
	printf("\n");
}

int main(int argc, char **argv) {
	vector<char> cmd;

	printf("start\n");
	if (argc > 1) {
		for (int i = 1; i < argc; i++) {
			if (i > 1) {
				cmd.add(' ');
			}
			cmd.insert(cmd.length(), argv[i], strlen(argv[i]));
		}
		cmd.add(0);
		printf("%s\n", executeret(cmd.getbuf()));
	}
	printf("end\n");
	exit(0);
}
