struct vec4;

struct vec
{
    union
    {
        struct { float x, y, z; };
        float v[3];
    };

    vec() {}
    explicit vec(int a) : x(a), y(a), z(a) {} 
    explicit vec(float a) : x(a), y(a), z(a) {} 
    vec(float a, float b, float c) : x(a), y(b), z(c) {}
    vec(int v[3]) : x(v[0]), y(v[1]), z(v[2]) {}
    vec(float *v) : x(v[0]), y(v[1]), z(v[2]) {}
    explicit vec(const vec4 &v);

    vec(float yaw, float pitch) : x(sinf(yaw)*cosf(pitch)), y(-cosf(yaw)*cosf(pitch)), z(sinf(pitch)) {}

    float &operator[](int i)       { return v[i]; }
    float  operator[](int i) const { return v[i]; }
    
    vec &set(int i, float f) { v[i] = f; return *this; }

    bool operator==(const vec &o) const { return x == o.x && y == o.y && z == o.z; }
    bool operator!=(const vec &o) const { return x != o.x || y != o.y || z != o.z; }

    bool iszero() const { return x==0 && y==0 && z==0; }
    float squaredlen() const { return x*x + y*y + z*z; }
    float dot(const vec &o) const { return x*o.x + y*o.y + z*o.z; }
    vec &mul(float f)        { x *= f; y *= f; z *= f; return *this; }
    vec &div(float f)        { x /= f; y /= f; z /= f; return *this; }
    vec &add(const vec &o)   { x += o.x; y += o.y; z += o.z; return *this; }
    vec &add(float f)        { x += f; y += f; z += f; return *this; }
    vec &sub(const vec &o)   { x -= o.x; y -= o.y; z -= o.z; return *this; }
    vec &sub(float f)        { x -= f; y -= f; z -= f; return *this; }
    vec &neg()               { return mul(-1); }
    float magnitude() const  { return sqrtf(squaredlen()); }
    vec &normalize()         { div(magnitude()); return *this; }
    bool isnormalized() const { float m = squaredlen(); return (m>0.99f && m<1.01f); }
    float dist(const vec &e) const { vec t; return dist(e, t); }
    float dist(const vec &e, vec &t) const { t = *this; t.sub(e); return t.magnitude(); }
    bool reject(const vec &o, float max) { return x>o.x+max || x<o.x-max || y>o.y+max || y<o.y-max; }
    vec &cross(const vec &a, const vec &b) { x = a.y*b.z-a.z*b.y; y = a.z*b.x-a.x*b.z; z = a.x*b.y-a.y*b.x; return *this; }
    vec &reflect(const vec &n) { float k = 2*dot(n); x -= k*n.x; y -= k*n.y; z -= k*n.z; return *this; }
    vec &project(const vec &n) { float k = dot(n); x -= k*n.x; y -= k*n.y; z -= k*n.z; return *this; }
    vec &projectxydir(const vec &n) { if(n.z) z = -(x*n.x/n.z + y*n.y/n.z); return *this; }
    vec &projectxy(const vec &n)
    {
        float m = squaredlen(), k = dot(n);
        projectxydir(n);
        rescale(sqrtf(max(m - k*k, 0.0f)));
        return *this;
    }
    vec &projectxy(const vec &n, float threshold)
    {
        float m = squaredlen(), k = min(dot(n), threshold);
        projectxydir(n);
        rescale(sqrtf(max(m - k*k, 0.0f)));
        return *this;
    }
    void lerp(const vec &a, const vec &b, float t) { x = a.x*(1-t)+b.x*t; y = a.y*(1-t)+b.y*t; z = a.z*(1-t)+b.z*t; }

    void rescale(float k)
    {
        float mag = magnitude();
        if(mag > 1e-6f) mul(k / mag);
    }

    void rotate_around_z(float angle) { *this = vec(cosf(angle)*x-sinf(angle)*y, cosf(angle)*y+sinf(angle)*x, z); }
    void rotate_around_x(float angle) { *this = vec(x, cosf(angle)*y-sinf(angle)*z, cosf(angle)*z+sinf(angle)*y); }
    void rotate_around_y(float angle) { *this = vec(cosf(angle)*x-sinf(angle)*z, y, cosf(angle)*z+sinf(angle)*x); }

    void rotate(float angle, const vec &d)
    {
        float c = cosf(angle), s = sinf(angle);
        rotate(c, s, d);
    }

    void rotate(float c, float s, const vec &d)
    {
        *this = vec(x*(d.x*d.x*(1-c)+c) + y*(d.x*d.y*(1-c)-d.z*s) + z*(d.x*d.z*(1-c)+d.y*s),
                    x*(d.y*d.x*(1-c)+d.z*s) + y*(d.y*d.y*(1-c)+c) + z*(d.y*d.z*(1-c)-d.x*s),
                    x*(d.x*d.z*(1-c)-d.y*s) + y*(d.y*d.z*(1-c)+d.x*s) + z*(d.z*d.z*(1-c)+c));
    }

    void orthogonal(const vec &d)
    {
        int i = fabs(d.x) > fabs(d.y) ? (fabs(d.x) > fabs(d.z) ? 0 : 2) : (fabs(d.y) > fabs(d.z) ? 1 : 2); 
        v[i] = d[(i+1)%3];
        v[(i+1)%3] = -d[i];
        v[(i+2)%3] = 0;
    }

    void orthonormalize(vec &s, vec &t) const
    {
        s.sub(vec(*this).mul(dot(s)));
        t.sub(vec(*this).mul(dot(t)))
         .sub(vec(s).mul(s.dot(t)));
    }

    template<class T> float dist_to_bb(const T &min, const T &max) const
    {
        float sqrdist = 0;
        loopi(3)
        {
            if     (v[i] < min[i]) { float delta = v[i]-min[i]; sqrdist += delta*delta; }
            else if(v[i] > max[i]) { float delta = max[i]-v[i]; sqrdist += delta*delta; }
        }
        return sqrtf(sqrdist);
    }

    template<class T, class S> float dist_to_bb(const T &o, S size) const
    {
        return dist_to_bb(o, T(o).add(size));
    }
};

struct vec4
{
    union
    {
        struct { float x, y, z, w; };
        float v[4];
    };

    vec4() {}
    explicit vec4(const vec &p, float w = 0) : x(p.x), y(p.y), z(p.z), w(w) {}
    vec4(float x, float y, float z, float w) : x(x), y(y), z(z), w(w) {}

    float &operator[](int i)       { return v[i]; }
    float  operator[](int i) const { return v[i]; }

    float dot3(const vec4 &o) const { return x*o.x + y*o.y + z*o.z; }
    float dot3(const vec &o) const { return x*o.x + y*o.y + z*o.z; }
    float dot(const vec4 &o) const { return dot3(o) + w*o.w; }
    float dot(const vec &o) const  { return x*o.x + y*o.y + z*o.z + w; }
    float squaredlen() const { return dot(*this); }
    float magnitude() const  { return sqrtf(squaredlen()); }
    float magnitude3() const { return sqrtf(dot3(*this)); }
    vec4 &normalize() { mul(1/magnitude()); return *this; }

    void lerp(const vec4 &a, const vec4 &b, float t) 
    { 
        x = a.x*(1-t)+b.x*t; 
        y = a.y*(1-t)+b.y*t; 
        z = a.z*(1-t)+b.z*t;
        w = a.w*(1-t)+b.w*t;
    }

    vec4 &mul3(float f)       { x *= f; y *= f; z *= f; return *this; }
    vec4 &mul(float f)       { mul3(f); w *= f; return *this; }
    vec4 &add(const vec4 &o) { x += o.x; y += o.y; z += o.z; w += o.w; return *this; }
    vec4 &neg3()             { x = -x; y = -y; z = -z; return *this; }
    vec4 &neg()              { neg3(); w = -w; return *this; }
};

inline vec::vec(const vec4 &v) : x(v.x), y(v.y), z(v.z) {}

struct quat : vec4
{
    quat() {}
    quat(float x, float y, float z, float w) : vec4(x, y, z, w) {}
    quat(const vec &axis, float angle)
    {
        w = cosf(angle/2);
        float s = sinf(angle/2);
        x = s*axis.x;
        y = s*axis.y;
        z = s*axis.z;
    }
 
    void restorew() { w = 1.0f-x*x-y*y-z*z; w = w<0 ? 0 : -sqrtf(w); }

    void add(const vec4 &o) { vec4::add(o); }
    void mul(float k) { vec4::mul(k); }

    void mul(const quat &p, const quat &o)
    {
        x = p.w*o.x + p.x*o.w + p.y*o.z - p.z*o.y;
        y = p.w*o.y - p.x*o.z + p.y*o.w + p.z*o.x;
        z = p.w*o.z + p.x*o.y - p.y*o.x + p.z*o.w;
        w = p.w*o.w - p.x*o.x - p.y*o.y - p.z*o.z;
    }
    void mul(const quat &o) { mul(quat(*this), o); }

    void mul(const quat &p, const vec &o)
    {
        x =  p.w*o.x + p.y*o.z - p.z*o.y;
        y =  p.w*o.y - p.x*o.z + p.z*o.x;
        z =  p.w*o.z + p.x*o.y - p.y*o.x;
        w = -p.x*o.x - p.y*o.y - p.z*o.z;
    }
    void mul(const vec &o) { mul(quat(*this), o); }

    quat &invert() { neg3(); return *this; }

    void slerp(const quat &from, const quat &to, float t)
    {
        float cosomega = from.dot(to), fromk, tok = 1;
        if(cosomega<0) { cosomega = -cosomega; tok = -1; }

        if(cosomega > 1 - 1e-6) { fromk = 1-t; tok *= t; }
        else
        {
            float omega = acosf(cosomega), recipsinomega = 1/sinf(omega);
            fromk = sinf((1-t)*omega)*recipsinomega;
            tok *= sinf(t*omega)*recipsinomega;
        }

        loopi(4) v[i] = from[i]*fromk + to[i]*tok;
    }

    vec rotate(const vec &v) const
    {
        vec t1, t2;
        t1.cross(vec(*this), v);
        t2.cross(vec(*this), t1);
        t1.mul(w).add(t2).mul(2).add(v);
        return t1;
#if 0
        quat inv(*this);
        inv.invert();
        inv.normalize();
        quat tmp;
        tmp.mul(*this, v);
        tmp.mul(inv);
        return vec(tmp.x, tmp.y, tmp.z);
#endif
    }
};

struct dualquat
{
    quat real, dual;

    dualquat() {}
    dualquat(const quat &q, const vec &p) : real(q)
    {
        dual.x =  0.5f*( p.x*q.w + p.y*q.z - p.z*q.y);
        dual.y =  0.5f*(-p.x*q.z + p.y*q.w + p.z*q.x);
        dual.z =  0.5f*( p.x*q.y - p.y*q.x + p.z*q.w);
        dual.w = -0.5f*( p.x*q.x + p.y*q.y + p.z*q.z);
    }
    explicit dualquat(const quat &q) : real(q), dual(0, 0, 0, 0) {}
    
    dualquat &mul(float k) { real.mul(k); dual.mul(k); return *this; }
    dualquat &add(const dualquat &d) { real.add(d.real); dual.add(d.dual); return *this; }

    void lerp(const dualquat &from, const dualquat &to, float t)
    {
        float a = 1-t, b = from.real.dot(to.real)<0 ? -t : t;
        loopi(4)
        {
            real[i] = from.real[i]*a + to.real[i]*b;
            dual[i] = from.dual[i]*a + to.dual[i]*b;
        }
    }

    dualquat &invert()
    {
        float rr = real.squaredlen();
        if(rr > 0)
        {
            float invrr = 1/rr,
                  invrd = -2*real.dot(dual)*invrr*invrr;

            dual.mul3(-invrr);
            dual.w *= invrr;
            quat tmp(real);
            tmp.mul3(-invrd);
            tmp.w *= invrd;
            dual.add(tmp);

            real.mul3(-invrr);
            real.w *= invrr;
        }
        else { real = dual = quat(0, 0, 0, 0); }
        return *this;
    }
    
    void mul(const dualquat &p, const dualquat &o)
    {
        real.mul(p.real, o.real);
        dual.mul(p.real, o.dual);
        quat tmp;
        tmp.mul(p.dual, o.real);
        dual.add(tmp);
    }       
    void mul(const dualquat &o) { mul(dualquat(*this), o); }    
    
    void normalize()
    {
        float invlen = 1/real.magnitude();
        real.mul(invlen);
        dual.mul(invlen);
    }

    void translate(const vec &p)
    {
        dual.x +=  0.5f*( p.x*real.w + p.y*real.z - p.z*real.y);
        dual.y +=  0.5f*(-p.x*real.z + p.y*real.w + p.z*real.x);
        dual.z +=  0.5f*( p.x*real.y - p.y*real.x + p.z*real.w);
        dual.w += -0.5f*( p.x*real.x + p.y*real.y + p.z*real.z);
    }

    void scale(float k)
    {
        dual.mul(k);
    }

    void fixantipodal(const dualquat &d)
    {
        if(real.dot(d.real) < 0)
        {
            real.neg();
            dual.neg();
        }
    }

    void accumulate(const dualquat &d, float k)
    {
        real.add(vec4(d.real).mul(k));
        dual.add(vec4(d.dual).mul(k));
    }

    vec transform(const vec &v) const
    {
        vec t1, t2;
        t1.cross(vec(real), v);
        t1.add(vec(v).mul(real.w));
        t2.cross(vec(real), t1);

        vec t3;
        t3.cross(vec(real), vec(dual));
        t3.add(vec(dual).mul(real.w));
        t3.sub(vec(real).mul(dual.w));

        t2.add(t3).mul(2).add(v);

        return t2;
    }
};

struct matrix3x4
{
    vec4 X, Y, Z;
    
    matrix3x4() {}
    matrix3x4(const vec4 &x, const vec4 &y, const vec4 &z) : X(x), Y(y), Z(z) {}
    matrix3x4(const dualquat &d)
    {
        float x = d.real.x, y = d.real.y, z = d.real.z, w = d.real.w, 
              ww = w*w, xx = x*x, yy = y*y, zz = z*z,
              xy = x*y, xz = x*z, yz = y*z,
              wx = w*x, wy = w*y, wz = w*z;
        X = vec4(ww + xx - yy - zz, 2*(xy - wz), 2*(xz + wy),
            -2*(d.dual.w*x - d.dual.x*w + d.dual.y*z - d.dual.z*y));
        Y = vec4(2*(xy + wz), ww + yy - xx - zz, 2*(yz - wx),
            -2*(d.dual.w*y - d.dual.x*z - d.dual.y*w + d.dual.z*x));
        Z = vec4(2*(xz - wy), 2*(yz + wx), ww + zz - xx - yy,
            -2*(d.dual.w*z + d.dual.x*y - d.dual.y*x - d.dual.z*w));

        float invrr = 1/d.real.dot(d.real);
        X.mul(invrr);
        Y.mul(invrr);
        Z.mul(invrr);
    }

    void scale(float k)
    {
        X.mul(k);
        Y.mul(k);
        Z.mul(k);
    }

    void translate(const vec &p)
    {
        X.w += p.x;
        Y.w += p.y;
        Z.w += p.z;
    }

    void accumulate(const matrix3x4 &m, float k)
    {
        X.add(vec4(m.X).mul(k));
        Y.add(vec4(m.Y).mul(k));
        Z.add(vec4(m.Z).mul(k));
    }

    void normalize()
    {
        X.mul3(1/X.magnitude3());
        Y.mul3(1/Y.magnitude3());
        Z.mul3(1/Z.magnitude3());
    }

    void lerp(const matrix3x4 &from, const matrix3x4 &to, float t)
    {
        loopi(4)
        {
            X[i] += to.X[i]*t + from.X[i]*(1-t);
            Y[i] += to.Y[i]*t + from.Y[i]*(1-t);
            Z[i] += to.Z[i]*t + from.Z[i]*(1-t);
        }
    }

    void identity()
    {
        X = vec4(1, 0, 0, 0);
        Y = vec4(0, 1, 0, 0);
        Z = vec4(0, 0, 1, 0);
    }

    void mul(const matrix3x4 &m, const matrix3x4 &n)
    {
        X = vec4(m.X.dot3(vec(n.X.x, n.Y.x, n.Z.x)),
                 m.X.dot3(vec(n.X.y, n.Y.y, n.Z.y)),
                 m.X.dot3(vec(n.X.z, n.Y.z, n.Z.z)),
                 m.X.dot(vec(n.X.w, n.Y.w, n.Z.w)));
        Y = vec4(m.Y.dot3(vec(n.X.x, n.Y.x, n.Z.x)),
                 m.Y.dot3(vec(n.X.y, n.Y.y, n.Z.y)),
                 m.Y.dot3(vec(n.X.z, n.Y.z, n.Z.z)),
                 m.Y.dot(vec(n.X.w, n.Y.w, n.Z.w)));
        Z = vec4(m.Z.dot3(vec(n.X.x, n.Y.x, n.Z.x)),
                 m.Z.dot3(vec(n.X.y, n.Y.y, n.Z.y)),
                 m.Z.dot3(vec(n.X.z, n.Y.z, n.Z.z)),
                 m.Z.dot(vec(n.X.w, n.Y.w, n.Z.w)));
    }
    void mul(const matrix3x4 &n) { mul(matrix3x4(*this), n); }

    void rotate(float angle, const vec &d)
    {
        float c = cosf(angle), s = sinf(angle);
        rotate(c, s, d);
    }

    void rotate(float c, float s, const vec &d)
    {
        X = vec4(d.x*d.x*(1-c)+c, d.x*d.y*(1-c)-d.z*s, d.x*d.z*(1-c)+d.y*s, 0);
        Y = vec4(d.y*d.x*(1-c)+d.z*s, d.y*d.y*(1-c)+c, d.y*d.z*(1-c)-d.x*s, 0);
        Z = vec4(d.x*d.z*(1-c)-d.y*s, d.y*d.z*(1-c)+d.x*s, d.z*d.z*(1-c)+c, 0);
    }

    vec transform(const vec &o) const { return vec(X.dot(o), Y.dot(o), Z.dot(o)); }
    vec transformnormal(const vec &o) const { return vec(X.dot3(o), Y.dot3(o), Z.dot3(o)); }
    vec transposedtransformnormal(const vec &o) const
    {
        return vec(X.x*o.x + Y.x*o.y + Z.x*o.z,
                   X.y*o.x + Y.y*o.y + Z.y*o.z,
                   X.z*o.x + Y.z*o.y + Z.z*o.z);
    }
};

struct plane : vec
{
    float offset;

    float dist(const vec &p) const { return dot(p)+offset; }
    bool operator==(const plane &p) const { return x==p.x && y==p.y && z==p.z && offset==p.offset; }
    bool operator!=(const plane &p) const { return x!=p.x || y!=p.y || z!=p.z || offset!=p.offset; }

    plane() {}
    plane(vec &c, float off) : vec(c), offset(off) {} 
    plane(int d, float off)
    {
        x = y = z = 0.0f;
        v[d] = 1.0f;
        offset = -off;
    }
    plane(float a, float b, float c, float d) : vec(a, b, c), offset(d) {}

    void toplane(const vec &n, const vec &p)
    {
        x = n.x; y = n.y; z = n.z;
        offset = -dot(p);
    }

    bool toplane(const vec &a, const vec &b, const vec &c)
    {
        cross(vec(b).sub(a), vec(c).sub(a));
        float mag = magnitude();
        if(!mag) return false;
        div(mag);
        offset = -dot(a);
        return true;
    }

    bool rayintersect(const vec &o, const vec &ray, float &dist)
    {
        float cosalpha = dot(ray);
        if(cosalpha==0) return false;
        float deltac = offset+dot(o);
        dist -= deltac/cosalpha;
        return true;
    }

    float zintersect(const vec &p) const { return -(x*p.x+y*p.y+offset)/z; }
    float zdist(const vec &p) const { return p.z-zintersect(p); }
};

struct triangle
{
    vec a, b, c;

    triangle(const vec &a, const vec &b, const vec &c) : a(a), b(b), c(c) {}
    triangle() {}

    triangle &add(const vec &o) { a.add(o); b.add(o); c.add(o); return *this; }
    triangle &sub(const vec &o) { a.sub(o); b.sub(o); c.sub(o); return *this; }

    bool operator==(const triangle &t) const { return a == t.a && b == t.b && c == t.c; }
};

/**

Sauerbraten uses 3 different linear coordinate systems
which are oriented around each of the axis dimensions.

So any point within the game can be defined by four coordinates: (d, x, y, z)

d is the reference axis dimension
x is the coordinate of the ROW dimension
y is the coordinate of the COL dimension
z is the coordinate of the reference dimension (DEPTH)

typically, if d is not used, then it is implicitly the Z dimension.
ie: d=z => x=x, y=y, z=z

**/

// DIM: X=0 Y=1 Z=2.
const int R[3]  = {1, 2, 0}; // row
const int C[3]  = {2, 0, 1}; // col
const int D[3]  = {0, 1, 2}; // depth

struct ivec
{
    union
    {
        struct { int x, y, z; };
        int v[3];
    };

    ivec() {}
    ivec(const vec &v) : x(int(v.x)), y(int(v.y)), z(int(v.z)) {}
    ivec(int i)
    {
        x = ((i&1)>>0);
        y = ((i&2)>>1);
        z = ((i&4)>>2);
    }
    ivec(int a, int b, int c) : x(a), y(b), z(c) {}
    ivec(int d, int row, int col, int depth)
    {
        v[R[d]] = row;
        v[C[d]] = col;
        v[D[d]] = depth;
    }
    ivec(int i, int cx, int cy, int cz, int size)
    {
        x = cx+((i&1)>>0)*size;
        y = cy+((i&2)>>1)*size;
        z = cz+((i&4)>>2)*size;
    }
    vec tovec() const { return vec(x, y, z); }
    int toint() const { return (x>0?1:0) + (y>0?2:0) + (z>0?4:0); }

    int &operator[](int i)       { return v[i]; }
    int  operator[](int i) const { return v[i]; }

    //int idx(int i) { return v[i]; }
    bool operator==(const ivec &v) const { return x==v.x && y==v.y && z==v.z; }
    bool operator!=(const ivec &v) const { return x!=v.x || y!=v.y || z!=v.z; }
    bool iszero() const { return x==0 && y==0 && z==0; }
    ivec &shl(int n) { x<<= n; y<<= n; z<<= n; return *this; }
    ivec &shr(int n) { x>>= n; y>>= n; z>>= n; return *this; }
    ivec &mul(int n) { x *= n; y *= n; z *= n; return *this; }
    ivec &div(int n) { x /= n; y /= n; z /= n; return *this; }
    ivec &add(int n) { x += n; y += n; z += n; return *this; }
    ivec &sub(int n) { x -= n; y -= n; z -= n; return *this; }
    ivec &add(const ivec &v) { x += v.x; y += v.y; z += v.z; return *this; }
    ivec &sub(const ivec &v) { x -= v.x; y -= v.y; z -= v.z; return *this; }
    ivec &mask(int n) { x &= n; y &= n; z &= n; return *this; }
    ivec &neg() { return mul(-1); }
    ivec &cross(const ivec &a, const ivec &b) { x = a.y*b.z-a.z*b.y; y = a.z*b.x-a.x*b.z; z = a.x*b.y-a.y*b.x; return *this; }
    int dot(const ivec &o) const { return x*o.x + y*o.y + z*o.z; }
    float dist(const plane &p) const { return x*p.x + y*p.y + z*p.z + p.offset; }
};

static inline bool htcmp(const ivec &x, const ivec &y)
{
    return x == y;
}  

static inline uint hthash(const ivec &k)
{
    return k.x^k.y^k.z;
}  

struct svec
{
    union
    {
        struct { ushort x, y, z; };
        ushort v[3];
    };

    svec() {}
    svec(ushort x, ushort y, ushort z) : x(x), y(y), z(z) {}

    ushort &operator[](int i)       { return v[i]; }
    ushort  operator[](int i) const { return v[i]; }

    bool operator==(const svec &v) const { return x==v.x && y==v.y && z==v.z; }
    bool operator!=(const svec &v) const { return x!=v.x || y!=v.y || z!=v.z; }

    svec &add(const svec &o) { x += o.x; y += o.y; z += o.z; return *this; }
    svec &add(const ivec &o) { x += o.x; y += o.y; z += o.z; return *this; }
    svec &add(int n) { x += n; y += n; z += n; return *this; }
    svec &sub(const svec &o) { x -= o.x; y -= o.y; z -= o.z; return *this; }
    svec &sub(const ivec &o) { x -= o.x; y -= o.y; z -= o.z; return *this; }
    svec &sub(int n) { x -= n; y -= n; z -= n; return *this; }
    svec &mul(int f) { x *= f; y *= f; z *= f; return *this; }
    svec &div(int f) { x /= f; y /= f; z /= f; return *this; }

    bool iszero() const { return x==0 && y==0 && z==0; }
};

struct bvec
{
    union
    {
        struct { uchar x, y, z; };
        uchar v[3];
    };

    bvec() {}
    bvec(uchar x, uchar y, uchar z) : x(x), y(y), z(z) {}
    bvec(const vec &v) : x((uchar)((v.x+1)*255/2)), y((uchar)((v.y+1)*255/2)), z((uchar)((v.z+1)*255/2)) {}

    uchar &operator[](int i)       { return v[i]; }
    uchar  operator[](int i) const { return v[i]; }

    bool operator==(const bvec &v) const { return x==v.x && y==v.y && z==v.z; }
    bool operator!=(const bvec &v) const { return x!=v.x || y!=v.y || z!=v.z; }

    bool iszero() const { return x==0 && y==0 && z==0; }

    vec tovec() const { return vec(x*(2.0f/255.0f)-1.0f, y*(2.0f/255.0f)-1.0f, z*(2.0f/255.0f)-1.0f); }
};

extern bool raysphereintersect(vec c, float radius, const vec &o, const vec &ray, float &dist);
extern bool rayrectintersect(const vec &b, const vec &s, const vec &o, const vec &ray, float &dist, int &orient);

enum { INTERSECT_NONE, INTERSECT_OVERLAP, INTERSECT_BEFORESTART, INTERSECT_MIDDLE, INTERSECT_AFTEREND };
extern int intersect_plane_line(vec &linestart, vec &linestop, vec &planeorig, vec &planenormal, vec &intersectionpoint);

