#ifndef WINDOWS
#define _ndebugf(n, args...) {static int _count = (n); if (_count-- > 0) {printf( args);}}
#define ndebugf(n, args...) 
#else
#define _ndebugf(n, args, ...) {static int _count = (n); if (_count-- > 0) {printf(__VA_ARGS__);}}
#define ndebugf(n, args, ...) 
#endif


#ifdef WINDOWS
#define snprintf _snprintf
#endif
