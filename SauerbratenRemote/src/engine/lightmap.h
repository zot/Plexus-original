#define LM_MINW 2
#define LM_MINH 2
#define LM_MAXW 128
#define LM_MAXH 128
#define LM_PACKW 512
#define LM_PACKH 512

struct PackNode
{
    PackNode *child1, *child2;
    ushort x, y, w, h;
    int available;

    PackNode() : child1(0), child2(0), x(0), y(0), w(LM_PACKW), h(LM_PACKH), available(min(LM_PACKW, LM_PACKH)) {}
    PackNode(ushort x, ushort y, ushort w, ushort h) : child1(0), child2(0), x(x), y(y), w(w), h(h), available(min(w, h)) {}

    void clear()
    {
        DELETEP(child1);
        DELETEP(child2);
    }

    ~PackNode()
    {
        clear();
    }

    bool insert(ushort &tx, ushort &ty, ushort tw, ushort th);
};

enum { LM_DIFFUSE = 0, LM_BUMPMAP0, LM_BUMPMAP1 };

struct LightMap
{
    int type, tex, offsetx, offsety;
    PackNode packroot;
    uint lightmaps, lumels;
    int unlitx, unlity; 
    uchar data[3 * LM_PACKW * LM_PACKH];

    LightMap()
     : type(LM_DIFFUSE), tex(-1), offsetx(-1), offsety(-1),
       lightmaps(0), lumels(0), unlitx(-1), unlity(-1)
    {
        memset(data, 0, sizeof(data));
    }

    void finalize()
    {
        packroot.clear();
        packroot.available = 0;
    }

    bool insert(ushort &tx, ushort &ty, uchar *src, ushort tw, ushort th);
};

extern vector<LightMap> lightmaps;

struct LightMapTexture
{
    int w, h, type;
    GLuint id;
    int unlitx, unlity;

    LightMapTexture()
     : w(0), h(0), type(LM_DIFFUSE), id(0), unlitx(-1), unlity(-1)
    {}
};

extern vector<LightMapTexture> lightmaptexs;

enum { LMID_AMBIENT = 0, LMID_AMBIENT1, LMID_BRIGHT, LMID_BRIGHT1, LMID_DARK, LMID_DARK1, LMID_RESERVED };

extern void clearlights();
extern void initlights();
extern void clearlightcache(int e = -1);
extern void resetlightmaps();
extern void newsurfaces(cube &c);
extern void freesurfaces(cube &c);
extern void brightencube(cube &c);

struct lerpvert
{
    vec normal;
    float u, v;

    bool operator==(const lerpvert &l) const { return u == l.u && v == l.v; }
    bool operator!=(const lerpvert &l) const { return u != l.u || v != l.v; }
};
    
struct lerpbounds
{
    const lerpvert *min;
    const lerpvert *max;
    float u, ustep;
    vec normal, nstep;
};

extern void calcnormals();
extern void clearnormals();
extern void findnormal(const ivec &origin, const vvec &offset, const vec &surface, vec &v);
extern void calclerpverts(const vec &origin, const vec *p, const vec *n, const vec &ustep, const vec &vstep, lerpvert *lv, int &numv);
extern void initlerpbounds(const lerpvert *lv, int numv, lerpbounds &start, lerpbounds &end);
extern void lerpnormal(float v, const lerpvert *lv, int numv, lerpbounds &start, lerpbounds &end, vec &normal, vec &nstep);

extern void newnormals(cube &c);
extern void freenormals(cube &c);

#define CHECK_CALCLIGHT_PROGRESS(exit, show_calclight_progress) \
    if(check_calclight_progress) \
    { \
        if(!calclight_canceled) \
        { \
            show_calclight_progress(); \
            check_calclight_canceled(); \
        } \
        if(calclight_canceled) exit; \
    }

extern bool calclight_canceled;
extern volatile bool check_calclight_progress;

extern void check_calclight_canceled();

