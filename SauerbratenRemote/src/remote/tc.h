#define _ndebugf(n, args...) {static int _count = (n); if (_count-- > 0) {printf( args);}}
#define ndebugf(n, args...) 
