package mupq;

import java.util.List;
import java.util.Arrays;

import com.sun.jna.Platform;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;

public class PseudoTerminal {

    @FieldOrder({"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc", "c_ispeed", "c_ospeed"})
    public class TermiosMac extends Structure {
        public NativeLong c_iflag;
        public NativeLong c_oflag;
        public NativeLong c_cflag;
        public NativeLong c_lflag;
        public byte[] c_cc = new byte[20];
        public NativeLong c_ispeed;
        public NativeLong c_ospeed;
    }

    public interface LibTermiosMac extends Library {
        long ECHO = 0x8;

        long ICANON = 0x100;

        void cfmakeraw(TermiosMac temios);

        int tcgetattr(int fd, TermiosMac termios);

        int tcsetattr(int fd, int cmd, TermiosMac termios);

        LibTermiosMac INSTANCE = Native.load("c", LibTermiosMac.class);
    }

    @FieldOrder({"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_line", "c_cc", "c_ispeed", "c_ospeed"})
    public class TermiosLinux extends Structure {

        public int c_iflag;
        public int c_oflag;
        public int c_cflag;
        public int c_lflag;
        public byte c_line;
        public byte[] c_cc = new byte[32];
        public int c_ispeed;
        public int c_ospeed;
    }

    public interface LibTermiosLinux extends Library {
        long ECHO = 0x8;

        long ICANON = 0x2;

        void cfmakeraw(TermiosLinux temios);

        int tcgetattr(int fd, TermiosLinux termios);

        int tcsetattr(int fd, int cmd, TermiosLinux termios);

        LibTermiosLinux INSTANCE = Native.load("c", LibTermiosLinux.class);
    }

    public interface LibC extends Library {
        String DEV_PTMX = "/dev/ptmx";

        int O_RDWR = 2;

        int O_NONBLOCK_LINUX = 2048;
        int O_NONBLOCK_MAC = 4;

        int open(String pathname, int flags);

        int write(int fd, byte[] buf, int len);

        int read(int fd, byte[] buf, int len);

        int grantpt(int fd);

        int unlockpt(int fd);

        String ptsname(int fd);

        LibC INSTANCE = Native.load("c", LibC.class);
    }

    private int pts;

    private String ptsname;

    public PseudoTerminal() throws Exception {
        int nonblock;
        if (Platform.isLinux()) {
            nonblock = LibC.O_NONBLOCK_LINUX;
        } else if (Platform.isMac()) {
            nonblock = LibC.O_NONBLOCK_MAC;
        } else {
            throw new Exception("Unsupported Platform");
        }
        pts = LibC.INSTANCE.open(LibC.DEV_PTMX, LibC.O_RDWR | nonblock);
        if (pts == -1 || LibC.INSTANCE.grantpt(pts) == -1 || LibC.INSTANCE.unlockpt(pts) == -1) {
            throw new Exception("Cannot open pseudoterminal");
        }
        ptsname = LibC.INSTANCE.ptsname(pts);
        if (Platform.isLinux()) {
            TermiosLinux tmios = new TermiosLinux();
            LibTermiosLinux.INSTANCE.tcgetattr(pts, tmios);
            LibTermiosLinux.INSTANCE.cfmakeraw(tmios);
            LibTermiosLinux.INSTANCE.tcsetattr(pts, 0, tmios);
        } else if (Platform.isMac()) {
            /* TODO: Untested */
            TermiosMac tmios = new TermiosMac();
            LibTermiosMac.INSTANCE.tcgetattr(pts, tmios);
            LibTermiosMac.INSTANCE.cfmakeraw(tmios);
            LibTermiosMac.INSTANCE.tcsetattr(pts, 0, tmios);
        }
    }

    public String getPtsName() {
        return ptsname;
    }

    public synchronized int writeByte(int b) {
        byte[] buf = new byte[1];
        buf[0] = (byte) (b & 0xFF);
        int res = LibC.INSTANCE.write(pts, buf, 1);
        if (res == 1) {
            return res;
        } else {
            return -1;
        }
    }

    public synchronized int readByte() {
        byte[] buf = new byte[1];
        int res = LibC.INSTANCE.read(pts, buf, 1);
        if (res == 1) {
            return ((int) buf[0]) & 0xFF;
        } else {
            return -1;
        }
    }
}
