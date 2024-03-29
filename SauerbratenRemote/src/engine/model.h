enum { MDL_MD2 = 1, MDL_MD3, MDL_MD5, MDL_OBJ };

struct model
{
    float spin, offsetyaw, offsetpitch;
    bool collide, ellipsecollide, cullface, shadow;
    float scale;
    vec translate;
    BIH *bih;
    vec bbcenter, bbradius;
    float eyeheight, collideradius, collideheight;
    int batch;

    model() : spin(0), offsetyaw(0), offsetpitch(0), collide(true), ellipsecollide(false), cullface(true), shadow(true), scale(1.0f), translate(0, 0, 0), bih(0), bbcenter(0, 0, 0), bbradius(0, 0, 0), eyeheight(0.9f), collideradius(0), collideheight(0), batch(-1) {}
    virtual ~model() { DELETEP(bih); }
    virtual void calcbb(int frame, vec &center, vec &radius) = 0;
    virtual void extendbb(int frame, vec &center, vec &radius, modelattach &a) {}
    virtual void render(int anim, float speed, int basetime, const vec &o, float yaw, float pitch, dynent *d, modelattach *a = NULL, const vec &color = vec(0, 0, 0), const vec &dir = vec(0, 0, 0)) = 0;
    virtual bool load() = 0;
    virtual char *name() = 0;
    virtual int type() const = 0;
    virtual BIH *setBIH() { return 0; }
    virtual bool envmapped() { return false; }

    virtual void setshader(Shader *shader) {}
    virtual void setenvmap(float envmapmin, float envmapmax, Texture *envmap) {}
    virtual void setspec(float spec) {}
    virtual void setambient(float ambient) {}
    virtual void setglow(float glow) {}
    virtual void setglare(float specglare, float glowglare) {}
    virtual void setalphatest(float alpha) {}
    virtual void setalphablend(bool blend) {}
    virtual void settranslucency(float translucency) {}
    virtual void setfullbright(float fullbright) {}

    virtual void cleanup() {}

    virtual void startrender() {}
    virtual void endrender() {}

    void boundbox(int frame, vec &center, vec &radius, modelattach *a = NULL)
    {
        if(frame) calcbb(frame, center, radius);
        else
        {
            if(bbradius.iszero()) calcbb(0, bbcenter, bbradius);
            center = bbcenter;
            radius = bbradius;
        }
        if(a) for(int i = 0; a[i].name; i++) if(a[i].m) extendbb(frame, center, radius, a[i]);
    }

    void collisionbox(int frame, vec &center, vec &radius)
    {
        boundbox(frame, center, radius);
        if(collideradius)
        {
            center[0] = center[1] = 0;
            radius[0] = radius[1] = collideradius;
        }
        if(collideheight)
        {
            center[2] = radius[2] = collideheight/2;
        }
    }

    float boundsphere(int frame, vec &center, modelattach *a = NULL)
    {
        vec radius;
        boundbox(frame, center, radius, a);
        return radius.magnitude();
    }

    float above(int frame = 0)
    {
        vec center, radius;
        boundbox(frame, center, radius);
        return center.z+radius.z;
    }
};

