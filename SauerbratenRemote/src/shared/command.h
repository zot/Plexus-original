// script binding functionality


enum { ID_VAR, ID_FVAR, ID_SVAR, ID_COMMAND, ID_CCOMMAND, ID_ALIAS };

enum { NO_OVERRIDE = INT_MAX, OVERRIDDEN = 0 };

enum { IDF_PERSIST = 1<<0, IDF_OVERRIDE = 1<<1 };

struct identstack
{
    char *action;
    identstack *next;
};

union identval
{
    int i;      // ID_VAR
    float f;    // ID_FVAR
    char *s;    // ID_SVAR
};

union identvalptr
{
    int *i;   // ID_VAR
    float *f; // ID_FVAR
    char **s; // ID_SVAR
};

struct ident
{
    int type;           // one of ID_* above
    const char *name;
    int minval, maxval; // ID_VAR
    int override;       // either NO_OVERRIDE, OVERRIDDEN, or value
    union
    {
        void (__cdecl *fun)(); // ID_VAR, ID_COMMAND, ID_CCOMMAND
        identstack *stack;     // ID_ALIAS
    };
    union
    {
        const char *narg; // ID_COMMAND, ID_CCOMMAND
        char *action;     // ID_ALIAS
        identval val;     // ID_VAR, ID_FVAR, ID_SVAR
    };
    union
    {
        void *self;           // ID_COMMAND, ID_CCOMMAND 
        char *isexecuting;    // ID_ALIAS
        identval overrideval; // ID_VAR, ID_FVAR, ID_SVAR
    };
    identvalptr storage; // ID_VAR, ID_FVAR, ID_SVAR
    int flags;
    
    ident() {}
    // ID_VAR
    ident(int t, const char *n, int m, int c, int x, int *s, void *f = NULL, int flags = 0)
        : type(t), name(n), minval(m), maxval(x), override(NO_OVERRIDE), fun((void (__cdecl *)())f), flags(flags)
    { val.i = c; storage.i = s; }
    // ID_FVAR
    ident(int t, const char *n, float c, float *s, void *f = NULL, int flags = 0)
        : type(t), name(n), override(NO_OVERRIDE), fun((void (__cdecl *)())f), flags(flags)
    { val.f = c; storage.f = s; }
    // ID_SVAR
    ident(int t, const char *n, char *c, char **s, void *f = NULL, int flags = 0)
        : type(t), name(n), override(NO_OVERRIDE), fun((void (__cdecl *)())f), flags(flags)
    { val.s = c; storage.s = s; }
    // ID_ALIAS
    ident(int t, const char *n, char *a, int flags)
        : type(t), name(n), override(NO_OVERRIDE), stack(NULL), action(a), flags(flags) {}
    // ID_COMMAND, ID_CCOMMAND
    ident(int t, const char *n, const char *narg, void *f = NULL, void *s = NULL, int flags = 0)
        : type(t), name(n), fun((void (__cdecl *)(void))f), narg(narg), self(s), flags(flags) {}

    virtual ~ident() {}        

    ident &operator=(const ident &o) { memcpy(this, &o, sizeof(ident)); return *this; }        // force vtable copy, ugh
    
    virtual void changed() { if(fun) fun(); }
};

extern void addident(const char *name, ident *id);
extern void intret(int v);
extern void result(const char *s);

// nasty macros for registering script functions, abuses globals to avoid excessive infrastructure
#define COMMANDN(name, fun, nargs) static bool __dummy_##fun = addcommand(#name, (void (*)())fun, nargs)
#define COMMAND(name, nargs) COMMANDN(name, name, nargs)

#define _VAR(name, global, min, cur, max, persist)  int global = variable(#name, min, cur, max, &global, NULL, persist)
#define VARN(name, global, min, cur, max) _VAR(name, global, min, cur, max, 0)
#define VARNP(name, global, min, cur, max) _VAR(name, global, min, cur, max, IDF_PERSIST)
#define VARNR(name, global, min, cur, max) _VAR(name, global, min, cur, max, IDF_OVERRIDE)
#define VAR(name, min, cur, max) _VAR(name, name, min, cur, max, 0)
#define VARP(name, min, cur, max) _VAR(name, name, min, cur, max, IDF_PERSIST)
#define VARR(name, min, cur, max) _VAR(name, name, min, cur, max, IDF_OVERRIDE)
#define _VARF(name, global, min, cur, max, body, persist)  void var_##name(); int global = variable(#name, min, cur, max, &global, var_##name, persist); void var_##name() { body; }
#define VARFN(name, global, min, cur, max, body) _VARF(name, global, min, cur, max, body, 0)
#define VARF(name, min, cur, max, body) _VARF(name, name, min, cur, max, body, 0)
#define VARFP(name, min, cur, max, body) _VARF(name, name, min, cur, max, body, IDF_PERSIST)
#define VARFR(name, min, cur, max, body) _VARF(name, name, min, cur, max, body, IDF_OVERRIDE)

#define _FVAR(name, global, cur, persist) float global = fvariable(#name, cur, &global, NULL, persist)
#define FVARN(name, global, cur) _FVAR(name, global, cur, 0)
#define FVARNP(name, global, cur) _FVAR(name, global, cur, IDF_PERSIST)
#define FVARNR(name, global, cur) _FVAR(name, global, cur, IDF_OVERRIDE)
#define FVAR(name, cur) _FVAR(name, name, cur, 0)
#define FVARP(name, cur) _FVAR(name, name, cur, IDF_PERSIST)
#define FVARR(name, cur) _FVAR(name, name, cur, IDF_OVERRIDE)
#define _FVARF(name, global, cur, body, persist) void var_##name(); float global = fvariable(#name, cur, &global, var_##name, persist); void var_##name() { body; }
#define FVARFN(name, global, cur, body) _FVARF(name, global, cur, body, 0)
#define FVARF(name, cur, body) _FVARF(name, name, cur, body, 0)
#define FVARFP(name, cur, body) _FVARF(name, name, cur, body, IDF_PERSIST)
#define FVARFR(name, cur, body) _FVARF(name, name, cur, body, IDF_OVERRIDE)

#define _SVAR(name, global, cur, persist) char *global = svariable(#name, cur, &global, NULL, persist)
#define SVARN(name, global, cur) _SVAR(name, global, cur, 0)
#define SVARNP(name, global, cur) _SVAR(name, global, cur, IDF_PERSIST)
#define SVARNR(name, global, cur) _SVAR(name, global, cur, IDF_OVERRIDE)
#define SVAR(name, cur) _SVAR(name, name, cur, 0)
#define SVARP(name, cur) _SVAR(name, name, cur, IDF_PERSIST)
#define SVARR(name, cur) _SVAR(name, name, cur, IDF_OVERRIDE)
#define _SVARF(name, global, cur, body, persist) void var_##name(); char *global = svariable(#name, cur, &global, var_##name, persist); void var_##name() { body; }
#define SVARFN(name, global, cur, body) _SVARF(name, global, cur, body, 0)
#define SVARF(name, cur, body) _SVARF(name, name, cur, body, 0)
#define SVARFP(name, cur, body) _SVARF(name, name, cur, body, IDF_PERSIST)
#define SVARFR(name, cur, body) _SVARF(name, name, cur, body, IDF_OVERRIDE)

// new style macros, have the body inline, and allow binds to happen anywhere, even inside class constructors, and access the surrounding class
#define _COMMAND(idtype, tv, n, g, proto, b) \
    struct cmd_##n : ident \
    { \
        cmd_##n(void *self = NULL) : ident(idtype, #n, g, (void *)run, self) \
        { \
            addident(name, this); \
        } \
        static void run proto { b; } \
    } icom_##n tv
#define ICOMMAND(n, g, proto, b) _COMMAND(ID_COMMAND, , n, g, proto, b)
#define CCOMMAND(n, g, proto, b) _COMMAND(ID_CCOMMAND, (this), n, g, proto, b)
 
#define _IVAR(n, m, c, x, b, p) \
    struct var_##n : ident \
    { \
        var_##n() : ident(ID_VAR, #n, m, c, x, &val.i, NULL, p) \
        { \
            addident(name, this); \
        } \
        int operator()() { return val.i; } \
        b \
    } n
#define IVAR(n, m, c, x)  _IVAR(n, m, c, x, , 0)
#define IVARF(n, m, c, x, b) _IVAR(n, m, c, x, void changed() { b; }, 0)
#define IVARP(n, m, c, x)  _IVAR(n, m, c, x, , IDF_PERSIST)
#define IVARR(n, m, c, x)  _IVAR(n, m, c, x, , IDF_OVERRIDE)
#define IVARFP(n, m, c, x, b) _IVAR(n, m, c, x, void changed() { b; }, IDF_PERSIST)
#define IVARFR(n, m, c, x, b) _IVAR(n, m, c, x, void changed() { b; }, IDF_OVERRIDE)
//#define ICALL(n, a) { char *args[] = a; icom_##n.run(args); }
//
