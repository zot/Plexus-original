struct BIHNode
{
    short split[2];
    ushort child[2];

    int axis() const { return child[0]>>14; }
    int childindex(int which) const { return child[which]&0x3FFF; }
    bool isleaf(int which) const { return (child[1]&(1<<(14+which)))!=0; }
};

struct BIH
{
    struct tri : triangle
    {
        float tc[6];
        Texture *tex;
    };

    int maxdepth;
    int numnodes;
    BIHNode *nodes;
    int numtris;
    tri *tris, *noclip;

    vec bbmin, bbmax;

    BIH(vector<tri> *tris);

    ~BIH()
    {
        DELETEA(nodes);
        DELETEA(tris);
    }

	static bool triintersect(tri &t, const vec &o, const vec &ray, float maxdist, float &dist, int mode, tri *noclip);

    void build(vector<BIHNode> &buildnodes, ushort *indices, int numindices, int depth = 1);

    bool traverse(const vec &o, const vec &ray, float maxdist, float &dist, int mode);
};

extern bool mmintersect(const extentity &e, const vec &o, const vec &ray, float maxdist, int mode, float &dist);

