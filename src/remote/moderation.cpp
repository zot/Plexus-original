#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <stdio.h>
#include <sys/types.h>
#include "remote.h"

typedef hashtable<const char *, ident> identtable;

static vec tmp;

struct watcher {
	char *id;
	dynent *entity;
	char *code;
	vec lastPosition;
	float lastRoll;
	float lastPitch;
	float lastYaw;
	float moveRes;
	float rotRes;
	
	watcher(char *id, char *code, float _moveRes, float _rotRes): moveRes(_moveRes), rotRes(_rotRes) {
		entity = getdynent(id);
		if (entity) {
			this->id = id;
			this->code = newstring(code);
			update();
		}
	}
	bool valid() {
		return !!entity;
	}
	void update() {
		if (moveRes > 0) {
			lastPosition = entity->o;
		}
		if (rotRes > 0) {
			lastRoll = entity->roll;
			lastPitch = entity->pitch;
			lastYaw = entity->yaw;
		}
	}
	bool changed() {
		if (moveRes > 0) {
			tmp = entity->o;
			tmp.sub(lastPosition);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
		}
		return rotRes > 0 && fabs(lastRoll - entity->roll) + fabs(lastPitch - entity->pitch) + fabs(lastYaw - entity->yaw) > rotRes;
	}
	void execute() {
		executeret(code);
	}
};

extern identtable *idents;        // contains ALL vars/commands/aliases

static char buf[1024];
char *currentcommandname = 0;
static vector<watcher> watchers;

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

ICOMMAND(override, "sss", (const char *cmd, char *newName, char *body), {
		printf("overriding cmd: %s as %s with definition: %s\n", cmd, newName, body);
		if (cmd && cmd[0] && newName && newName[0] && body && body[0]) {
			ident *old = idents->access(cmd);
			ident *newValue = idents->access(newName);

			if (newValue) {
				conoutf("cannot override cmd: %s as %s, because there is already a definition for %s.", cmd, newName, newName);
			} else if (!old) {
				conoutf("cannot override cmd: %s as %s, because there is no definition for %s.", cmd, newName, cmd);
			} else {
				int oldType = old->_type;

				idents->access(newstring(newName), old);
				old->_type = ID_ALIAS;
				old->_override = NO_OVERRIDE;
				old->_stack = NULL;
				old->_action = newstring(body);
				old->_persist = false;
				if (oldType != ID_ALIAS) {
					old->_name = newstring(old->_name);
				}
			}
		} else {
			conoutf("usage: overriding cmd newName body");
		}
});

static void moderationTick() {
	loopi(watchers.length()) {
		watcher *w = &watchers[i];
		tmp = w->entity->o;
		tmp.sub(w->lastPosition);
		if (w->changed()) {
			w->update();
			w->execute();
		}
	}
}

static void floatVal(float &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%f", fl);
		result(buf);
	} else {
		fl = atof(value);
	}
}

static void entX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.x, value);
		}
	}
}

static void entY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.y, value);
		}
	}
}

static void entZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.z, value);
		}
	}
}

static void entRoll(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->roll, value);
		}
	}
}

static void entPitch(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->pitch, value);
		}
	}
}

static void entYaw(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->yaw, value);
		}
	}
}

static bool initModeration() {
	printf("INITIALIZING\n");
	extern void addTickHook(void (*hook)());
	addTickHook(moderationTick);
	addcommand("ent.x", (void(*)())entX, "ss");
	addcommand("ent.y", (void(*)())entY, "ss");
	addcommand("ent.z", (void(*)())entZ, "ss");
	addcommand("ent.roll", (void(*)())entRoll, "ss");
	addcommand("ent.pitch", (void(*)())entPitch, "ss");
	addcommand("ent.yaw", (void(*)())entYaw, "ss");
	return true;
}
bool init = initModeration();

ICOMMAND(watch, "ss", (char *entName, char *code), {
		if (!entName || !entName[0]) {
			conoutf("no entity specified to watch");
		} else if (!code || !code[0]) {
			conoutf("no watching code given");
		} else {
			watcher w(entName, code, 1, 1);
			
			if (!w.valid()) {
				conoutf("no entity for id [%s]", entName);
			} else {
				watchers.add(w);
			}
		}
});
