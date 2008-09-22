#ifndef WINDOWS
#define _ndebugf(n, args...) {static int _count = (n); if (_count-- > 0) {printf( args);}}
#define ndebugf(n, args...)
#else
#define _ndebugf(n, args, ...) {static int _count = (n); if (_count-- > 0) {printf(__VA_ARGS__);}}
#define ndebugf(n, args, ...)
#endif


#ifdef WINDOWS
#define strdup _strdup
#define snprintf _snprintf
#endif

#ifndef __HUDIMAGEINFO__
#define __HUDIMAGEINFO__
	class hudimageinfo {
	public:
		char type[32], tc_var[256];
		int x, y, w, h;
	};
#endif
