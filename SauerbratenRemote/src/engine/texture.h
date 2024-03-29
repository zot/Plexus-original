// GL_ARB_vertex_program, GL_ARB_fragment_program
extern PFNGLGENPROGRAMSARBPROC              glGenPrograms_;
extern PFNGLDELETEPROGRAMSARBPROC           glDeletePrograms_;
extern PFNGLBINDPROGRAMARBPROC              glBindProgram_;
extern PFNGLPROGRAMSTRINGARBPROC            glProgramString_;
extern PFNGLGETPROGRAMIVARBPROC             glGetProgramiv_;
extern PFNGLPROGRAMENVPARAMETER4FARBPROC    glProgramEnvParameter4f_;
extern PFNGLPROGRAMENVPARAMETER4FVARBPROC   glProgramEnvParameter4fv_;
extern PFNGLENABLEVERTEXATTRIBARRAYARBPROC  glEnableVertexAttribArray_;
extern PFNGLDISABLEVERTEXATTRIBARRAYARBPROC glDisableVertexAttribArray_;
extern PFNGLVERTEXATTRIBPOINTERARBPROC      glVertexAttribPointer_;

// GL_EXT_gpu_program_parameters
#ifndef GL_EXT_gpu_program_parameters
#define GL_EXT_gpu_program_parameters 1
typedef void (APIENTRYP PFNGLPROGRAMENVPARAMETERS4FVEXTPROC) (GLenum target, GLuint index, GLsizei count, const GLfloat *params);
typedef void (APIENTRYP PFNGLPROGRAMLOCALPARAMETERS4FVEXTPROC) (GLenum target, GLuint index, GLsizei count, const GLfloat *params);
#endif

extern PFNGLPROGRAMENVPARAMETERS4FVEXTPROC   glProgramEnvParameters4fv_;
extern PFNGLPROGRAMLOCALPARAMETERS4FVEXTPROC glProgramLocalParameters4fv_;

// GL_ARB_shading_language_100, GL_ARB_shader_objects, GL_ARB_fragment_shader, GL_ARB_vertex_shader
extern PFNGLCREATEPROGRAMOBJECTARBPROC  glCreateProgramObject_;
extern PFNGLDELETEOBJECTARBPROC         glDeleteObject_;
extern PFNGLUSEPROGRAMOBJECTARBPROC     glUseProgramObject_;
extern PFNGLCREATESHADEROBJECTARBPROC   glCreateShaderObject_;
extern PFNGLSHADERSOURCEARBPROC         glShaderSource_;
extern PFNGLCOMPILESHADERARBPROC        glCompileShader_;
extern PFNGLGETOBJECTPARAMETERIVARBPROC glGetObjectParameteriv_;
extern PFNGLATTACHOBJECTARBPROC         glAttachObject_;
extern PFNGLGETINFOLOGARBPROC           glGetInfoLog_;
extern PFNGLLINKPROGRAMARBPROC          glLinkProgram_;
extern PFNGLGETUNIFORMLOCATIONARBPROC   glGetUniformLocation_;
extern PFNGLUNIFORM4FVARBPROC           glUniform4fv_;
extern PFNGLUNIFORM1IARBPROC            glUniform1i_;

extern int renderpath;

enum { R_FIXEDFUNCTION = 0, R_ASMSHADER, R_GLSLANG };

enum { SHPARAM_VERTEX = 0, SHPARAM_PIXEL, SHPARAM_UNIFORM };

#define RESERVEDSHADERPARAMS 16
#define MAXSHADERPARAMS 8

struct ShaderParam
{
    const char *name;
    int type, index, loc;
    float val[4];
};

struct LocalShaderParamState : ShaderParam
{
    float curval[4];

    LocalShaderParamState() 
    { 
        memset(curval, 0, sizeof(curval)); 
    }
    LocalShaderParamState(const ShaderParam &p) : ShaderParam(p)
    {
        memset(curval, 0, sizeof(curval));
    }
};

struct ShaderParamState
{
    enum
    {
        CLEAN = 0,
        INVALID,
        DIRTY
    };
    
    const char *name;
    float val[4];
    bool local;
    int dirty;

    ShaderParamState()
        : name(NULL), local(false), dirty(INVALID)
    {
        memset(val, 0, sizeof(val));
    }
};

enum 
{ 
    SHADER_INVALID    = -1,

    SHADER_DEFAULT    = 0, 
    SHADER_NORMALSLMS = 1<<0, 
    SHADER_ENVMAP     = 1<<1,
    SHADER_GLSLANG    = 1<<2
};

#define MAXSHADERDETAIL 3
#define MAXVARIANTROWS 5

extern int shaderdetail;

struct Slot;

struct Shader
{
    static Shader *lastshader;

    char *name, *vsstr, *psstr;
    int type;
    GLuint vs, ps;
    GLhandleARB program, vsobj, psobj;
    vector<LocalShaderParamState> defaultparams;
    Shader *variantshader, *altshader, *fastshader[MAXSHADERDETAIL];
    vector<Shader *> variants[MAXVARIANTROWS];
    bool standard, used, native;
    Shader *reusevs, *reuseps;
    int numextparams;
    LocalShaderParamState *extparams;
    uchar *extvertparams, *extpixparams;


    Shader() : name(NULL), vsstr(NULL), psstr(NULL), type(SHADER_DEFAULT), vs(0), ps(0), program(0), vsobj(0), psobj(0), variantshader(NULL), altshader(NULL), standard(false), used(false), native(true), reusevs(NULL), reuseps(NULL), numextparams(0), extparams(NULL), extvertparams(NULL), extpixparams(NULL)
    {}

    ~Shader()
    {
        DELETEA(name);
        DELETEA(vsstr);
        DELETEA(psstr);
        DELETEA(extparams);
        DELETEA(extvertparams);
        extpixparams = NULL;
    }

    void allocenvparams(Slot *slot = NULL);
    void flushenvparams(Slot *slot = NULL);
    void setslotparams(Slot &slot);
    void bindprograms();

    Shader *hasvariant(int col, int row = 0)
    {
        if(!this || renderpath==R_FIXEDFUNCTION) return NULL;
        Shader *s = shaderdetail < MAXSHADERDETAIL ? fastshader[shaderdetail] : this;
        return row>=0 && row<MAXVARIANTROWS && s->variants[row].inrange(col) ? s->variants[row][col] : NULL;
    }

    Shader *variant(int col, int row = 0)
    {
        if(!this || renderpath==R_FIXEDFUNCTION) return this;
        Shader *s = shaderdetail < MAXSHADERDETAIL ? fastshader[shaderdetail] : this;
        return row>=0 && row<MAXVARIANTROWS && s->variants[row].inrange(col) ? s->variants[row][col] : s;
    }

    void set(Slot *slot = NULL)
    {
        if(!this || renderpath==R_FIXEDFUNCTION) return;
        if(this!=lastshader)
        {
            if(shaderdetail < MAXSHADERDETAIL) fastshader[shaderdetail]->bindprograms();
            else bindprograms();
        }
        lastshader->flushenvparams(slot);
        if(slot) lastshader->setslotparams(*slot);
    }

    bool compile();
    void cleanup(bool invalid = false);
};

#define SETSHADER(name) \
    do { \
        static Shader *name##shader = NULL; \
        if(!name##shader) name##shader = lookupshaderbyname(#name); \
        name##shader->set(); \
    } while(0)

// management of texture slots
// each texture slot can have multiple texture frames, of which currently only the first is used
// additional frames can be used for various shaders

struct Texture
{
    enum
    {
        STUB,
        TRANSIENT,
        IMAGE,
        CUBEMAP
    };

    char *name;
    int type, w, h, xs, ys, bpp, clamp;
    bool mipmap, canreduce;
    GLuint id;
    uchar *alphamask;

    Texture() : alphamask(NULL) {}
};

enum
{
    TEX_DIFFUSE = 0,
    TEX_UNKNOWN,
    TEX_DECAL,
    TEX_NORMAL,
    TEX_GLOW,
    TEX_SPEC,
    TEX_DEPTH,
    TEX_ENVMAP
};
    
struct Slot
{
    struct Tex
    {
        int type;
        Texture *t;
        string name;
        int combined;
    };

    vector<Tex> sts;
    Shader *shader;
    vector<ShaderParam> params;
    float scale;
    int rotation, xoffset, yoffset;
    float scrollS, scrollT;
    vec glowcolor, pulseglowcolor;
    float pulseglowspeed;
    bool mtglowed, loaded;
    uint texmask;
    char *autograss;
    Texture *grasstex, *thumbnail;

    Slot() : autograss(NULL) { reset(); }
    
    void reset()
    {
        sts.setsize(0);
        shader = NULL;
        params.setsize(0);
        scale = 1;
        rotation = xoffset = yoffset = 0;
        scrollS = scrollT = 0;
        glowcolor = vec(1, 1, 1);
        pulseglowcolor = vec(0, 0, 0);
        pulseglowspeed = 0;
        loaded = false;
        texmask = 0;
        DELETEA(autograss);
        grasstex = NULL;
        thumbnail = NULL;
    }

    void cleanup()
    {
        loaded = false;
        grasstex = NULL;
        thumbnail = NULL;
        loopv(sts) 
        {
            Tex &t = sts[i];
            t.t = NULL;
            t.combined = -1;
        }
    }
};

struct cubemapside
{
    GLenum target;
    const char *name;
    bool flipx, flipy, swapxy;
};

extern cubemapside cubemapsides[6];
extern Texture *notexture;
extern Shader *defaultshader, *rectshader, *notextureshader, *nocolorshader, *foggedshader, *foggednotextureshader, *stdworldshader;
extern int reservevpparams, maxvpenvparams, maxvplocalparams, maxfpenvparams, maxfplocalparams;

extern Shader *lookupshaderbyname(const char *name);
extern Texture *loadthumbnail(Slot &slot);
extern void setslotshader(Slot &s);
extern void setenvparamf(const char *name, int type, int index, float x = 0, float y = 0, float z = 0, float w = 0);
extern void setenvparamfv(const char *name, int type, int index, const float *v);
extern void flushenvparamf(const char *name, int type, int index, float x = 0, float y = 0, float z = 0, float w = 0);
extern void flushenvparamfv(const char *name, int type, int index, const float *v);
extern void setlocalparamf(const char *name, int type, int index, float x = 0, float y = 0, float z = 0, float w = 0);
extern void setlocalparamfv(const char *name, int type, int index, const float *v);
extern void invalidateenvparams(int type, int start, int count);
extern ShaderParam *findshaderparam(Slot &s, const char *name, int type, int index);

extern int maxtmus, nolights, nowater, nomasks;

extern void inittmus();
extern void resettmu(int n);
extern void scaletmu(int n, int rgbscale, int alphascale = 0);
extern void colortmu(int n, float r = 0, float g = 0, float b = 0, float a = 0);
extern void setuptmu(int n, const char *rgbfunc = NULL, const char *alphafunc = NULL);

#define MAXDYNLIGHTS 5
#define DYNLIGHTBITS 6
#define DYNLIGHTMASK ((1<<DYNLIGHTBITS)-1)

#define MAXBLURRADIUS 7

extern void setupblurkernel(int radius, float sigma, float *weights, float *offsets);
extern void setblurshader(int pass, int size, int radius, float *weights, float *offsets, GLenum target = GL_TEXTURE_2D);

