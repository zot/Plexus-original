#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <stdio.h>
#include <sys/types.h>

typedef hashtable<const char *, ident> identtable;

extern identtable *idents;        // contains ALL vars/commands/aliases

ICOMMAND(listidents, "", (),
    enumerate(*idents, ident, id,
    	switch (id._type) {
    	case ID_VAR:
   			printf("var: %s %d\n", id._name, id._val);
   			break;
    	case ID_COMMAND:
   			printf("command: %s(%s)\n", id._name, id._narg);
   			break;
    	case ID_CCOMMAND:
   			printf("ccommand: %s(%s)\n", id._name, id._narg);
   			break;
    	case ID_ALIAS:
   			printf("alias: %s = [%s]\n", id._name, id._action);
    		break;
    	}
    );
);
