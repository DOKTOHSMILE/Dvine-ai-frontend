/*
 * DVINE's terminal device. Three calls Java cannot make on Android 14+: open a pseudo-terminal, learn its number
 * (with the slave unlocked), and set its size in rows and columns. No C library is used (only Linux system calls),
 * so this builds with any clang and loads on any arm64 Android. Built by build.sh into
 * src/main/jniLibs/arm64-v8a/libdvpty.so.
 *
 * Java side: ai.ibiko.linux.Pty.
 */
typedef int jint;
typedef void JNIEnv;
typedef void *jclass;

#define SYS_ioctl 29
#define SYS_openat 56
#define SYS_close 57
#define AT_FDCWD (-100)
#define O_RDWR 02
#define O_NOCTTY 0400
#define O_CLOEXEC 02000000
#define TIOCSPTLCK 0x40045431
#define TIOCGPTN 0x80045430
#define TIOCSWINSZ 0x5414

static long call3(long n, long a, long b, long c) {
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    __asm__ volatile("svc 0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory");
    return x0;
}

/* The master side's file descriptor, or a negative errno. */
__attribute__((visibility("default")))
jint Java_ai_ibiko_linux_Pty_nativeOpen(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) call3(SYS_openat, AT_FDCWD, (long) "/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
}

/* Unlocks the slave and returns its number (/dev/pts/N), or a negative errno. */
__attribute__((visibility("default")))
jint Java_ai_ibiko_linux_Pty_nativeNumber(JNIEnv *env, jclass cls, jint fd) {
    (void) env;
    (void) cls;
    int unlock = 0;
    long r = call3(SYS_ioctl, fd, TIOCSPTLCK, (long) &unlock);
    if (r < 0) {
        return (jint) r;
    }
    unsigned int n = 0;
    r = call3(SYS_ioctl, fd, TIOCGPTN, (long) &n);
    return r < 0 ? (jint) r : (jint) n;
}

/* Sets the terminal's size; programs inside are told (SIGWINCH). 0, or a negative errno. */
__attribute__((visibility("default")))
jint Java_ai_ibiko_linux_Pty_nativeResize(JNIEnv *env, jclass cls, jint fd, jint rows, jint cols) {
    (void) env;
    (void) cls;
    unsigned short size[4];
    size[0] = (unsigned short) rows;
    size[1] = (unsigned short) cols;
    size[2] = 0;
    size[3] = 0;
    return (jint) call3(SYS_ioctl, fd, TIOCSWINSZ, (long) size);
}

__attribute__((visibility("default")))
jint Java_ai_ibiko_linux_Pty_nativeClose(JNIEnv *env, jclass cls, jint fd) {
    (void) env;
    (void) cls;
    return (jint) call3(SYS_close, fd, 0, 0);
}
