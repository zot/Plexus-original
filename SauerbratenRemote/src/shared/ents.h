// this file defines static map entities ("entity") and dynamic entities (players/monsters, "dynent")
// the gamecode extends these types to add game specific functionality

// ET_*: the only static entity types dictated by the engine... rest are gamecode dependent

enum { ET_EMPTY=0, ET_LIGHT, ET_MAPMODEL, ET_PLAYERSTART, ET_ENVMAP, ET_PARTICLES, ET_SOUND, ET_SPOTLIGHT, ET_GAMESPECIFIC };

struct entity                                   // persistent map entity
{
    vec o;                                      // position
    short attr1, attr2, attr3, attr4, attr5;
    uchar type;                                 // type is one of the above
    uchar reserved;
};

enum
{
    TRIGGER_RESET = 0,
    TRIGGERING,
    TRIGGERED,
    TRIGGER_RESETTING,
    TRIGGER_DISAPPEARED
};

struct entitylight
{
    vec color, dir;
    int millis;

    entitylight() : color(1, 1, 1), dir(0, 0, 1), millis(-1) {}
};

struct extentity : entity                       // part of the entity that doesn't get saved to disk
{
    uchar spawned, inoctanode, visible, triggerstate;        // the only dynamic state of a map entity
    entitylight light;
    int lasttrigger;
    extentity *attached;
#ifdef TC
    int tc_id;
    extentity() : visible(false), triggerstate(TRIGGER_RESET), lasttrigger(0), attached(NULL), tc_id(0) {}
#else
    extentity() : visible(false), triggerstate(TRIGGER_RESET), lasttrigger(0), attached(NULL) {}
#endif
};

//extern vector<extentity *> ents;                // map entities

enum { CS_ALIVE = 0, CS_DEAD, CS_SPAWNING, CS_LAGGED, CS_EDITING, CS_SPECTATOR };

enum { PHYS_FLOAT = 0, PHYS_FALL, PHYS_SLIDE, PHYS_SLOPE, PHYS_FLOOR, PHYS_STEP_UP, PHYS_STEP_DOWN, PHYS_BOUNCE };

enum { ENT_PLAYER = 0, ENT_AI, ENT_INANIMATE, ENT_CAMERA, ENT_BOUNCE };

enum { COLLIDE_AABB = 0, COLLIDE_ELLIPSE };

struct physent                                  // base entity type, can be affected by physics
{
    vec o, vel, falling;                        // origin, velocity
    vec deltapos, newpos;                       // movement interpolation
    float yaw, pitch, roll;
    float maxspeed;                             // cubes per second, 100 for player
    int timeinair;
    float radius, eyeheight, aboveeye;          // bounding box size
    float xradius, yradius, zmargin;
    vec floor;                                  // the normal of floor the dynent is on

    int inwater;
    bool jumpnext;
    bool blocked, moving;                       // used by physics to signal ai
    physent *onplayer;
    int lastmove, lastmoveattempt, collisions, stacks;

    char move, strafe;

    uchar physstate;                            // one of PHYS_* above
    uchar state, editstate;                     // one of CS_* above
    uchar type;                                 // one of ENT_* above
    uchar collidetype;                          // one of COLLIDE_* above           

    physent() : o(0, 0, 0), deltapos(0, 0, 0), newpos(0, 0, 0), yaw(270), pitch(0), roll(0), maxspeed(100), 
               radius(4.1f), eyeheight(14), aboveeye(1), xradius(4.1f), yradius(4.1f), zmargin(0),
               blocked(false), moving(true), 
               onplayer(NULL), lastmove(0), lastmoveattempt(0), collisions(0), stacks(0),
               state(CS_ALIVE), editstate(CS_ALIVE), type(ENT_PLAYER),
               collidetype(COLLIDE_ELLIPSE)
               { reset(); }
              
    void resetinterp()
    {
        newpos = o;
        deltapos = vec(0, 0, 0);
    }

    void reset()
    {
    	inwater = 0;
        timeinair = 0;
        strafe = move = 0;
        physstate = PHYS_FALL;
        vel = falling = vec(0, 0, 0);
        floor = vec(0, 0, 1);
    }
};

enum
{
    ANIM_DEAD = 0, ANIM_DYING, ANIM_IDLE,
    ANIM_FORWARD, ANIM_BACKWARD, ANIM_LEFT, ANIM_RIGHT,
    ANIM_PUNCH, ANIM_SHOOT, ANIM_PAIN,
    ANIM_JUMP, ANIM_SINK, ANIM_SWIM,
    ANIM_EDIT, ANIM_LAG, ANIM_TAUNT, ANIM_WIN, ANIM_LOSE,
    ANIM_GUNSHOOT, ANIM_GUNIDLE,
    ANIM_VWEP, ANIM_SHIELD, ANIM_POWERUP,
    ANIM_MAPMODEL, ANIM_TRIGGER,
    NUMANIMS
};

#define ANIM_ALL         0xFF
#define ANIM_INDEX       0xFF
#define ANIM_LOOP        (1<<8)
#define ANIM_START       (1<<9)
#define ANIM_END         (1<<10)
#define ANIM_REVERSE     (1<<11)
#define ANIM_DIR         0xF00
#define ANIM_SECONDARY   12
#define ANIM_NOSKIN      (1<<24)
#define ANIM_ENVMAP      (1<<25)
#define ANIM_TRANSLUCENT (1<<26)
#define ANIM_SHADOW      (1<<27)
#define ANIM_SETTIME     (1<<28)
#define ANIM_FULLBRIGHT  (1<<29)
#define ANIM_REUSE       (1<<30)
#define ANIM_FLAGS       (0x7F<<24)

struct animinfo // description of a character's animation
{
    int anim, frame, range, basetime;
    float speed;
    uint varseed;

    animinfo() : anim(0), frame(0), range(0), basetime(0), speed(100.0f), varseed(0) { }

    bool operator==(const animinfo &o) const { return frame==o.frame && range==o.range && (anim&(ANIM_SETTIME|ANIM_DIR))==(o.anim&(ANIM_SETTIME|ANIM_DIR)) && (anim&ANIM_SETTIME || basetime==o.basetime) && speed==o.speed; }
    bool operator!=(const animinfo &o) const { return frame!=o.frame || range!=o.range || (anim&(ANIM_SETTIME|ANIM_DIR))!=(o.anim&(ANIM_SETTIME|ANIM_DIR)) || (!(anim&ANIM_SETTIME) && basetime!=o.basetime) || speed!=o.speed; }
};

struct animinterpinfo // used for animation blending of animated characters
{
    animinfo prev, cur;
    int lastswitch;
    void *lastmodel;

    animinterpinfo() : lastswitch(-1), lastmodel(NULL) {}
};

#define MAXANIMPARTS 2

struct occludequery;

#ifdef TC
struct fpsclient;
#endif

struct dynent : physent                         // animated characters, or characters that can receive input
{
    bool k_left, k_right, k_up, k_down;         // see input code
    float targetyaw, rotspeed;                  // AI rotation

    entitylight light;
    animinterpinfo animinterp[MAXANIMPARTS];
    occludequery *query;
    int occluded, lastrendered;
#ifdef TC
    char *modelname;
	int tc_id;
	void renderplayer(fpsclient &cl, const char *mdlname, void *attachments, int attack, int attackdelay, int lastaction, int lastpain, float sink = 0.0);
	void rendermonster(fpsclient &cl);
	void rendermovable(fpsclient &cl, vec o, const char *suggestion);

    dynent() : query(NULL), occluded(0), lastrendered(0), modelname(0), tc_id(0)
#else
    dynent() : query(NULL), occluded(0), lastrendered(0)
#endif
    { 
        reset(); 
    }
               
    void stopmoving()
    {
        k_left = k_right = k_up = k_down = jumpnext = false;
        move = strafe = 0;
        targetyaw = rotspeed = 0;
    }
        
    void reset()
    {
        physent::reset();
        stopmoving();
    }

    vec abovehead() { return vec(o).add(vec(0, 0, aboveeye+4)); }

    void normalize_yaw(float angle)
    {
        while(yaw<angle-180.0f) yaw += 360.0f;
        while(yaw>angle+180.0f) yaw -= 360.0f;
    }
};


