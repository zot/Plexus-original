#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include "game.h"
#include <stdio.h>
#include <sys/types.h>
#include "remote.h"
#include "tc.h"

//Millisecond value updated before each tick
int tickmillis;

typedef hashtable<const char *, ident> identtable;
VAR(appmouse, 0, 0, 1);
VAR(updateperiod, 50, 75, 3000);
#define executehook(s) executeret(s)

struct watcher {
	char *id;
	dynent *entity;
	char *code;
	vec lastPosition, lastVel, lastFalling;
	float lastRoll;
	float lastPitch;
	float lastYaw;
	float moveRes;
	float rotRes;
	int lastupdate;
    //int lastinwater, lasttimeinair;
	char laststrafe, lastmove;
	uchar lastphysstate;
	float lastmaxspeed;

	watcher(char *id, char *code, float _moveRes, float _rotRes): moveRes(_moveRes), rotRes(_rotRes), lastupdate(0) {
		entity = getdynent(id);
		if (entity) {
			this->id = newstring(id);
			this->code = newstring(code);
			update();
		} else {
			this->id = NULL;
			this->code = NULL;
		}
	}
	void dispose() {
		if (entity) delete entity;
		if (id) delete[] id;
		if (code) delete[] code;
	}
	bool valid() {
		return !!entity;
	}
	void update() {
		lastPosition = entity->o;
		lastVel = entity->vel;
		lastFalling = entity->falling;
		lastRoll = entity->roll;
		lastPitch = entity->pitch;
		lastYaw = entity->yaw;

		//lastinwater = entity->inwater;
		//lasttimeinair = entity->timeinair;
		laststrafe = entity->strafe;
		lastmove = entity->move;
		lastphysstate = entity->physstate;
		lastmaxspeed = entity->maxspeed;

		lastupdate = tickmillis;
	}
	bool changed() {
		if (tickmillis - lastupdate < updateperiod) {
			return false;
		}
		if (moveRes >= 0) {
			vec tmp = entity->o;
			tmp.sub(lastPosition);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
			tmp = entity->vel;
			tmp.sub(lastVel);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
			tmp = entity->falling;
			tmp.sub(lastFalling);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
		}
		if (//lastinwater != entity->inwater ||
			//lasttimeinair != entity->timeinair ||
			laststrafe != entity->strafe ||
			lastmove != entity->move ||
			lastphysstate != entity->physstate ||
			lastmaxspeed != entity->maxspeed) return true;

		if (rotRes >= 0) {
			if (fabs(lastRoll - entity->roll) + fabs(lastPitch - entity->pitch) + fabs(lastYaw - entity->yaw) > rotRes) return true;
		}
		return false;
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
    	switch (id.type) {
    	case ID_VAR:
   			printf("var: %s %d\n", id.name, id.val.i);
   			break;
    	case ID_COMMAND:
   			printf("command: %s(%s)\n", id.name, id.narg);
   			break;
    	case ID_CCOMMAND:
   			printf("ccommand: %s(%s)\n", id.name, id.narg);
   			break;
    	case ID_ALIAS:
   			printf("alias: %s = [%s]\n", id.name, id.action);
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
				int oldType = old->type;

				idents->access(newstring(newName), old);
				old->type = ID_ALIAS;
				old->override = NO_OVERRIDE;
				old->stack = NULL;
				old->action = newstring(body);
				//old->persist = false;
				if (oldType != ID_ALIAS) {
					old->name = newstring(old->name);
				}
			}
		} else {
			conoutf("usage: overriding cmd newName body");
		}
});

static void floatVal(float &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%f", fl);
		result(buf);
	} else {
		fl = atof(value);
	}
}

static void intVal(int &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", fl);
		result(buf);
	} else {
		fl = atoi(value);
	}
}

static void charVal(char &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", (int) fl);
		result(buf);
	} else {
		fl = (char) atoi(value);
	}
}

static void ucharVal(uchar &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", (int) fl);
		result(buf);
	} else {
		fl = (uchar) atoi(value);
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

static void entvX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.x, value);
		}
	}
}

static void entvY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.y, value);
		}
	}
}

static void entvZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.z, value);
		}
	}
}

static void entfX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.x, value);
		}
	}
}

static void entfY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.y, value);
		}
	}
}

static void entfZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.z, value);
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
			floatVal(ent->targetyaw, value);
			floatVal(ent->yaw, value);
		}
	}
}
static void entInWater(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) intVal(ent->inwater, value);
	}
}

static void entTimeInAir(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) intVal(ent->timeinair, value);
	}
}


static void entE(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			char e = ent->state == CS_EDITING;

			charVal(e, value);
			ent->state = e ? CS_EDITING : CS_ALIVE;
		}
	}
}

static void entStrafe(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) charVal(ent->strafe, value);
	}
}

static void entMove(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) charVal(ent->move, value);
	}
}

static void entPhysState(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) ucharVal(ent->physstate, value);
	}
}

static void entMaxSpeed(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) floatVal(ent->maxspeed, value);
	}
}

watcher *findWatcher(dynent *ent) {
	loopi(watchers.length()) {
		watcher *w = &watchers[i];
		if (NULL != w && w->entity == ent) return w;
	}
	return NULL;
}

static void report(char *output, char *field, float value)
{
	char mybuf[32];
	sprintf(mybuf, " %s %f", field, value);
	strcat(output, mybuf);
}

static void report(char *output, char *field, int value)
{
	char mybuf[32];
	sprintf(mybuf, " %s %d", field, value);
	strcat(output, mybuf);
}

static bool areDoublesEqual(double d1, double d2)
{
	return fabs(d1 - d2) < 0.00001;
}

static void tc_info(char *id) {
	buf[0] = '\0';

	if (id && id[0]) {
		dynent *ent = getdynent(id);
		watcher *w = findWatcher(ent);
		if (ent && w) {
			//fprintf(stderr, "tc_info have ent and watcher!\n");
#define REPORT_ALL 1
#ifdef REPORT_ALL
			report(buf, "x", ent->o.x);
			report(buf, "y", ent->o.y);
			report(buf, "z", ent->o.z);
			report(buf, "rol", ent->roll);
			report(buf, "pit", ent->pitch);
			report(buf, "yaw", ent->yaw);
			report(buf, "vx", ent->vel.x);
			report(buf, "vy", ent->vel.y);
			report(buf, "vz", ent->vel.z);
			report(buf, "fx", ent->falling.x);
			report(buf, "fy", ent->falling.y);
			report(buf, "fz", ent->falling.z);
			//report(buf, "iw", ent->inwater);
			//report(buf, "tia", ent->timeinair);
			report(buf, "e", ent->state == CS_EDITING);
			report(buf, "s", ent->strafe);
			report(buf, "m", ent->move);
			report(buf, "ps", ent->physstate);
			report(buf, "ms", ent->maxspeed);
#else
			if (!areDoublesEqual(ent->o.x, w->lastPosition.x)) report(buf, "x", ent->o.x);
			if (!areDoublesEqual(ent->o.y, w->lastPosition.y)) report(buf, "y", ent->o.y);
			if (!areDoublesEqual(ent->o.z, w->lastPosition.z)) report(buf, "z", ent->o.z);
			if (!areDoublesEqual(ent->roll, w->lastRoll)) report(buf, "rol", ent->roll);
			if (!areDoublesEqual(ent->pitch, w->lastPitch)) report(buf, "pit", ent->pitch);
			if (!areDoublesEqual(ent->yaw, w->lastYaw)) report(buf, "yaw", ent->yaw);

			if (!areDoublesEqual(ent->vel.x, w->lastVel.x)) report(buf, "vx", ent->vel.x);
			if (!areDoublesEqual(ent->vel.y, w->lastVel.y)) report(buf, "vy", ent->vel.y);
			if (!areDoublesEqual(ent->vel.z, w->lastVel.z)) report(buf, "vz", ent->vel.z);
			if (!areDoublesEqual(ent->falling.x, w->lastFalling.x)) report(buf, "fx", ent->falling.x);
			if (!areDoublesEqual(ent->falling.y, w->lastFalling.y)) report(buf, "fy", ent->falling.y);
			if (!areDoublesEqual(ent->falling.z, w->lastFalling.z)) report(buf, "fz", ent->falling.z);

			if (ent->edit != w->lastedit) report(buf, "e", ent->edit);
			if (ent->strafe != w->laststrafe) report(buf, "s", ent->strafe);
			if (ent->move != w->lastmove) report(buf, "m", ent->move);
			if (ent->physstate != w->lastphysstate) report(buf, "ps", ent->physstate);
			if (ent->maxspeed != w->lastmaxspeed) report(buf, "me", ent->maxspeed);
#endif
		} else {
			conoutf("tc_info error: no entity or watcher specified");
			strcpy(buf, "tc_info error");
		}
	}
	result(buf);
}

static void updateField(dynent *ent, char *f, char *value) {
	if (0 == strcmp(f, "x")) {
			floatVal(ent->o.x, value);
	} else if (0 == strcmp(f, "y")) {
			floatVal(ent->o.y, value);
	} else if (0 == strcmp(f, "z")) {
			floatVal(ent->o.z, value);
	} else if (0 == strcmp(f, "rol")) {
			floatVal(ent->roll, value);
	} else if (0 == strcmp(f, "pit")) {
			floatVal(ent->pitch, value);
	} else if (0 == strcmp(f, "yaw")) {
			floatVal(ent->yaw, value);
	} else if (0 == strcmp(f, "vx")) {
			floatVal(ent->vel.x, value);
	} else if (0 == strcmp(f, "vy")) {
			floatVal(ent->vel.y, value);
	} else if (0 == strcmp(f, "vz")) {
			floatVal(ent->vel.z, value);
	} else if (0 == strcmp(f, "fx")) {
			floatVal(ent->falling.x, value);
	} else if (0 == strcmp(f, "fy")) {
			floatVal(ent->falling.y, value);
	} else if (0 == strcmp(f, "fz")) {
			floatVal(ent->falling.z, value);
	} else if (0 == strcmp(f, "s")) {
			charVal(ent->strafe, value);
	} else if (0 == strcmp(f, "m")) {
			charVal(ent->move, value);
	} else if (0 == strcmp(f, "e")) {
			char e = '\0';
			charVal(e, value);
			if (e) ent->state = CS_EDITING;
	} else if (0 == strcmp(f, "ps")) {
			ucharVal(ent->physstate, value);
	} else if (0 == strcmp(f, "ms")) {
			floatVal(ent->maxspeed, value);
	} else {
		conoutf("tc_setinfo protocol sending unknown field: %s", f);
	}
}

static void tc_setinfo(char *info)
{
	dynent *ent = NULL;
	char *tok = NULL;

	if (info && info[0]) {
		//fprintf(stderr, "\ntc_setinfo: %s\n", info);
		char *id = strtok(info, " \t");
		//fprintf(stderr, "Player entity: %s\n", id);
		ent = getdynent(id);
	}
	if (!ent) return;

     float oldyaw = ent->yaw, oldpitch = ent->pitch;
     vec oldpos(ent->o);

	ent->state = CS_ALIVE;
	tok = strtok(NULL, " \t");
	while (NULL != tok) {
		char *f = tok;
		char *v = strtok(NULL, " \t");
		if (f && v) {
			//fprintf(stderr, "Setting property: %s -> %s\n", f, v);
			updateField(ent, f, v);
		} else break;
		tok = strtok(NULL, " \t");
	}
	// experimental change so u don't animate run while in edit mode
	if (ent->state == CS_EDITING) { ent->move = 0; ent->strafe = 0; }

	extern void interpolatePlayer(void *d, float oldyaw, float oldpitch, vec oldpos);
	interpolatePlayer(ent, oldyaw, oldpitch, oldpos);
	//fprintf(stderr, "States after interpolation s: %d m: %d\n", (int) ent->strafe, (int) ent->move);
}

void tc_edittrigger(const selinfo &sel, int op, int arg1, int arg2, int arg3)
{
	//fprintf(stderr, "Calling tc_edittrigger\n");
	const char *opType = NULL;

    switch(op)
    {
        case EDIT_FLIP:
        	opType = "tc_edit_flip_hook";
        case EDIT_COPY:
        	opType = opType ? opType : "tc_edit_copy_hook";
        case EDIT_PASTE:
        	opType = opType ? opType : "tc_edit_paste_hook";
        case EDIT_DELCUBE:
        {
        	opType = opType ? opType : "tc_edit_delcube_hook";
			snprintf(buf, sizeof(buf), "%s %d %d %d %d %d %d %d %d %d %d %d %d %d %d", opType, SV_EDITF + op,
               sel.o.x, sel.o.y, sel.o.z, sel.s.x, sel.s.y, sel.s.z, sel.grid, sel.orient,
               sel.cx, sel.cxs, sel.cy, sel.cys, sel.corner);
            break;
        }
        case EDIT_MAT:
        	opType = "tc_edit_mat_hook";
        case EDIT_ROTATE:
        {
        	opType = opType ? opType : "tc_edit_rotate_hook";
			snprintf(buf, sizeof(buf), "%s %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d", opType, SV_EDITF + op,
               sel.o.x, sel.o.y, sel.o.z, sel.s.x, sel.s.y, sel.s.z, sel.grid, sel.orient,
               sel.cx, sel.cxs, sel.cy, sel.cys, sel.corner,
               arg1);
            break;
        }
        case EDIT_FACE:
        	opType = "tc_edit_face_hook";
        case EDIT_TEX:
        	opType = opType ? opType : "tc_edit_tex_hook";
        case EDIT_REPLACE:
        {
        	opType = opType ? opType : "tc_edit_replace_hook";
			snprintf(buf, sizeof(buf), "%s %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d", opType, SV_EDITF + op,
               sel.o.x, sel.o.y, sel.o.z, sel.s.x, sel.s.y, sel.s.z, sel.grid, sel.orient,
               sel.cx, sel.cxs, sel.cy, sel.cys, sel.corner,
               arg1, arg2);
            break;
        }
        case EDIT_REMIP:
        {
			snprintf(buf, sizeof(buf), "tc_edit_remip_hook %d r", SV_EDITF + op);
            //cc.addmsg(SV_EDITF + op, "r");
            break;
        }
    }
    executehook(buf);
}

void tc_taunthook() {
	strcpy(buf, "tc_taunthook");
	executehook(buf);
}

static void moderationTick() {
	loopi(watchers.length()) {
		watcher *w = &watchers[i];
		if (w->changed()) {

			w->execute();
			w->update(); // update AFTER execute to reset last known info
		}
	}
	static int lastupdate = 0;
	if (tickmillis - lastupdate > updateperiod) {
		lastupdate = tickmillis;
		executehook("tc_tick_hook");
	}
}

bool safeParseInt(int &i)
{
	char *str = strtok(NULL, " \t");
	if (!str) return false;

	intVal(i, str); // 2
	return true;
}

static void tc_upmap(char *info)
{
	if (!info || !info[0]) return;

	char *t = strtok(info, " \t"); // 1
	if (NULL == t) return;
	int type = atoi(t);

	selinfo sel;
	if (!(
		safeParseInt(sel.o.x) &&
		safeParseInt(sel.o.y) &&
		safeParseInt(sel.o.z) &&
		safeParseInt(sel.s.x) &&
		safeParseInt(sel.s.y) &&
		safeParseInt(sel.s.z) &&
		safeParseInt(sel.grid) &&
		safeParseInt(sel.orient) &&
		safeParseInt(sel.cx) &&
		safeParseInt(sel.cxs) &&
		safeParseInt(sel.cy) &&
		safeParseInt(sel.cys) &&
		safeParseInt(sel.corner))) return;

	int dir, mode, tex, newtex, mat, allfaces;
	ivec moveo;
	fpsent *d = (fpsent *) getdynent("p0");   // not sure.. may have to pass in the player making the change!
	switch(type)
	{
	case SV_EDITF: if (safeParseInt(dir) && safeParseInt(mode)){ mpeditface(dir, mode, sel, false); } break;
	case SV_EDITT: if (safeParseInt(tex) && safeParseInt(allfaces)) { mpedittex(tex, allfaces, sel, false); } break;
	case SV_EDITM: if (safeParseInt(mat)) { mpeditmat(mat, sel, false); } break;
	case SV_FLIP: mpflip(sel, false); break;
	case SV_COPY: mpcopy(d->edit, sel, false); break;
	case SV_PASTE: mppaste(d->edit, sel, false); break;
	case SV_ROTATE: if (safeParseInt(dir)) { mprotate(dir, sel, false); } break;
	case SV_REPLACE: if (safeParseInt(tex) && safeParseInt(newtex)) { mpreplacetex(tex, newtex, sel, false); } break;
	case SV_DELCUBE: mpdelcube(sel, false); break;
	}
}

static void tc_editent(char *info)
{
	if (!info || !info[0]) return;

	char *t = strtok(info, " \t"); // 1
	if (NULL == t) return;
	int type = atoi(t);
	int i, x, y, z, attr1, attr2, attr3, attr4;

	safeParseInt(i);
	safeParseInt(x);
	safeParseInt(y);
	safeParseInt(z);
	safeParseInt(type);
	safeParseInt(attr1);
	safeParseInt(attr2);
	safeParseInt(attr3);
	safeParseInt(attr4);

    mpeditent(i, vec(x/DMF, y/DMF, z/DMF), type, attr1, attr2, attr3, attr4, false);
}

// hook to be called when a new map is created or loaded in
void tc_newmaphook(char *name)
{
	snprintf(buf, sizeof(buf), "tc_newmap_hook %s", name);
	executehook(buf);
}

void tc_editenttrigger(int i, entity &e)
{
	snprintf(buf, sizeof(buf), "tc_editent_hook %d %d %d %d %d %d %d %d %d %d",
		SV_EDITENT, i, (int)(e.o.x*DMF), (int)(e.o.y*DMF), (int)(e.o.z*DMF), e.type, e.attr1, e.attr2, e.attr3, e.attr4);
	executehook(buf);
}

static bool initModeration() {
	printf("INITIALIZING\n");
	extern void addTickHook(void (*hook)());
	addTickHook(moderationTick);

	addcommand("tc_info", (void(*)())tc_info, "s");
	addcommand("tc_setinfo", (void(*)())tc_setinfo, "C");
	addcommand("tc_upmap", (void(*)())tc_upmap, "C");
	addcommand("tc_editent", (void(*)())tc_editent, "C");
	addcommand("ent.x", (void(*)())entX, "ss");
	addcommand("ent.y", (void(*)())entY, "ss");
	addcommand("ent.z", (void(*)())entZ, "ss");
	addcommand("ent.rol", (void(*)())entRoll, "ss");
	addcommand("ent.pit", (void(*)())entPitch, "ss");
	addcommand("ent.yaw", (void(*)())entYaw, "ss");

	addcommand("ent.vx", (void(*)())entvX, "ss");
	addcommand("ent.vy", (void(*)())entvY, "ss");
	addcommand("ent.vz", (void(*)())entvZ, "ss");
	addcommand("ent.fx", (void(*)())entfX, "ss");
	addcommand("ent.fy", (void(*)())entfY, "ss");
	addcommand("ent.fz", (void(*)())entfZ, "ss");

	//addcommand("ent.iw", (void(*)())entInWater, "ss");
	//addcommand("ent.tia", (void(*)())entTimeInAir, "ss");
	addcommand("ent.e", (void(*)())entE, "ss");
	addcommand("ent.s", (void(*)())entStrafe, "ss");
	addcommand("ent.m", (void(*)())entMove, "ss");
	addcommand("ent.ps", (void(*)())entPhysState, "ss");
	return true;
}
bool init = initModeration();

ICOMMAND(watch, "sss", (char *entName, char *code, char *resolution), {
		if (!entName || !entName[0]) {
			conoutf("no entity specified to watch");
		} else if (!code) {
			conoutf("no watching code given");
		} else if (!code[0]) {
			loopi(watchers.length()) {
				watcher *w = &watchers[i];

				if (!strcmp(w->id, entName)) {
					if (w->id) {
						delete[] w->id;
					}
					if (w->code) {
						delete[] w->code;
					}
					watchers.remove(i);
					return;
				}
			}
		} else {
			loopi(watchers.length()) {
				watcher *w = &watchers[i];

				if (!strcmp(w->id, entName)) {
					if (w->code) {
						printf("REPLACING OLD CODE \"%s\" WITH ", w->code);
						delete[] w->code;
					}
					w->code = newstring(code);
					printf("NEW CODE \"%s\"\n", w->code);
					return;
				}
			}
			float res = 1.0;
			if (resolution && resolution[0]) floatVal(res, resolution);
			watcher w(entName, code, res, res);

			if (!w.valid()) {
				conoutf("no entity for id [%s]", entName);
			} else {
				watchers.add(w);
			}
		}
});

ICOMMAND(deleteplayer, "s", (char *ent), {
	if (ent && ent[0] == 'p') {
		// if we try to recreate a new player of the same id, just reuse the existing one
		fpsent *p = (fpsent *) getdynent(ent);
		if (NULL == p) {
			conoutf("/deleteplayer error: player %s not found", ent);
			return;
		}
		if (p->tc_id == 0) {
			conoutf("/deleteplayer error: cannot delete yourself!");
			return;
		}

		extern void deleteplayer(fpsent *p);
		deleteplayer(p);

		loopi(watchers.length()) {
			watcher &w = watchers[i];
			if (w.entity == p) {
				watchers.remove(i);
				w.dispose();
				return;
			}
		}
	}
});

ICOMMAND(maxspeed, "ss", (char *ent, char *speed), {
	if (ent && ent[0]) {
		dynent *d = getdynent(ent);

		if (d) {
			floatVal(d->maxspeed, speed);
		}
	}
});
static vector<char> strbuf;
ICOMMAND(bufadd, "C", (char *str), {
	int len = strlen(str);
	databuf<char> buf = strbuf.reserve(len + 1);
	buf.len = len;
	strcpy(buf.buf, str);
	strbuf.addbuf(buf);
});
ICOMMAND(bufget, "", (), {
	strbuf.add('\0');
	strbuf.setsize(0);
	result(strbuf.buf);
});
ICOMMAND(buflen, "", (), {
	intret(strbuf.length());
});
