# PQVexRiscV
[VexRiscv](https://github.com/SpinalHDL/VexRiscv) based Target platforms
for the [pqriscv](https://github.com/mupq/pqriscv) project

## Introduction
The goal of this project is to implement a simple test-platform for the
VexRiscv CPU, to be used as a reference platform for benchmarking and
experimenting with PQC scheme implementations.

## Setup
You'll need the following

* Java JDK **==** 1.8
* [SBT](https://www.scala-sbt.org) >= 1.2.8, the Scala Build Tool
* (For iCE40 based FPGAs) [Icestorm FPGA Toolchain](http://www.clifford.at/icestorm/), including [NextPNR](https://github.com/YosysHQ/nextpnr)
* (For Xilinx based FPGAs) Vivado ~= 2018.3 (probably also works with older and newer versions)
* (For the Simulator) [Verilator](https://www.veripool.org/wiki/verilator)
* (Optionally for Debugging) [OpenOCD for VexRiscv](https://github.com/SpinalHDL/openocd_riscv)

## Synthesis
This project (and VexRiscv) uses the SpinalHDL language, which is
essentially a DSL for hardware design on top of the Scala language.
The Scala sources will then generate equivalent Verilog (or VHDL) of
circuits described in SpinalHDL.
So to create a bitstream for a FPGA, you'll have to generate the verilog
file first and then run synthesis.

Just run `sbt` in the project root and run any of the main classes to
generate the verilog source for one of the targets.
The `rtl` folder contains a Makefile to generate the bitstream for the
FPGAs (the Makefile will also run `sbt` to generate the Verilog).
For example:

```sh
make -C rtl TARGETS=PQVexRiscvUP5K
```

## Simulation
Simulating a VexRiscv core is also possible.

```sh
sbt "runMain mupq.PQVexRiscvSim --init initfile.bin --ram 256,128 --uart uartoutput.txt"
```

Adapt the options to your liking. The `--init` option points to a binary
file which is loaded into the simulated RAM as initialization. Remove
the option to leave the RAM blank, you can also load your binary via
OpenOCD+GDB.

The `--ram` option determines the memory architecture. The
simulator will add a X KiB sized block of RAM to the memory architecture
for each integer. Default is two blocks of ram, one for meant for code,
one for data. The RAM blocks start at address `0x80000000` of the
VexRiscv core and are placed back-to-back.

The `--uart` block may point to a file to which the simulated UART
output of the core is appended. When this option is skipped, UART is
redirected to stdout.

## OpenOCD for VexRiscv
All boards (including the simulator) support debugging via a JTAG port.
For that you'll need a suitable debugging adapter (anything FT2232
should do) and [OpenOCD port for VexRiscv](https://github.com/SpinalHDL/openocd_riscv).
You can use the following to compile a stripped down version of the
tool, which also adds the suffix `-vexriscv` to the program name, as
well as placing all the data into a different dir, so the installation
won't clash with any other OpenOCD version on your system. Adapt the
prefix/datarootdir to your taste.

```sh
./bootstrap
./configure --prefix=/usr/local --program-suffix=-vexriscv \
  --datarootdir=/usr/local/share/vexriscv --enable-maintainer-mode \
  --disable-werror --enable-ft232r --enable-ftdi --enable-jtag_vpi \
  --disable-aice --disable-amtjtagaccel --disable-armjtagew \
  --disable-assert --disable-at91rm9200 --disable-bcm2835gpio \
  --disable-buspira --disable-cmsis-dap --disable-doxygen-html \
  --disable-doxygen-pdf --disable-dummy --disable-ep93xx \
  --disable-gw16012 --disable-imx_gpio --disable-jlink \
  --disable-kitprog --disable-minidriver-dummy --disable-oocd_trace \
  --disable-opendous --disable-openjtag --disable-osbdm \
  --disable-parport --disable-parport-giveio --disable-parport-ppdev \
  --disable-presto --disable-remote-bitbang --disable-rlink \
  --disable-stlink --disable-sysfsgpio --disable-ti-icdi \
  --disable-ulink --disable-usb-blaster --disable-usb-blaster-2 \
  --disable-usbprog --disable-verbose-jtag-io \
  --disable-verbose-usb-comms --disable-verbose-usb-io \
  --disable-vsllink --disable-xds110 --disable-zy1000 \
  --disable-zy1000-master
make
# sudo make install
```

Some templates for connecting OpenOCD and a Debug adapter to the boards
are at the ready in the project root. For example:

```sh
openocd-vexriscv -f pqvexriscvsim.cfg
```

Since OpenOCD opens up a gdbserver, you can debug on your target with
GDB. For example:

```sh
riscv64-unknown-elf-gdb -ex 'set remotetimeout 10' -ex 'target remote :3333' -ex load -ex 'break main' my_awesome_program.elf
```

## FPGA Platforms
This project will support multiple FPGA targets in future:

* [iCE40 UltraPlus 5K](https://www.latticesemi.com/en/Products/FPGAandCPLD/iCE40UltraPlus) as, for example, present in the [iCE40 UltraPlus Breakout Board](https://www.latticesemi.com/en/Products/DevelopmentBoardsAndKits/iCE40UltraPlusBreakoutBoard)
* WIP: [Icoboard](http://icoboard.org/), which in turn uses the [iCE40 HX 8K](https://www.latticesemi.com/Products/FPGAandCPLD/iCE40)
* WIP: [Xilinx 7 Series](https://www.xilinx.com/products/silicon-devices/fpga.html), with an example file for the [Digilent Arty A7 board](https://store.digilentinc.com/arty-a7-artix-7-fpga-development-board-for-makers-and-hobbyists/)

### iCE40 UltraPlus 5k
A basic design with the default VexRiscv plugins should fit on the iCE40
UP5K.
As the UP5K features 8 DSP blocks, a non-blocking multiplier will fit
comfortably.
Furthermore, four 32kb sized SRAM blocks are present on the FPGA, which
are used to implement two separate 64kb large blocks on the memory bus.
You should link your code to the lower half (starting `0x80000000`), and
stack / data to the upper half (starting `0x80010000`).

The `rtl` folder contains a constraint file to place the design on the
UltraPlus Breakout Board (though other boards should work fine, as long
as the use the UP5K, just adapt the PCF as necessary).
It exposes the JTAG and UART pins to IO ports located on header B as
follows:

| Function   | Pin     |
|------------|---------|
| JTAG `TDO` | iob_23b |
| JTAG `TCK` | iob_25b |
| JTAG `TDI` | iob_24a |
| JTAG `TMS` | iob_29b |
| UART `TXD` | iob_8a  |
| UART `RXD` | iob_9b  |

You can use any FTDI FT2232H or FT232H based debugger probe together
with `openocd-vexriscv`, just adapt the connection script
`PQVexRiscvUP5K.cfg` accordingly.
Tip: You could use the
[FT_PROG](https://www.ftdichip.com/Support/Utilities.htm#FT_PROG) to
change the serial number of a generic FT232H breakout board [(e,g, the
Adafruit FT232H Breakout board)](https://www.adafruit.com/product/2264).
Add a line `ftdi_serial "XXX"` to the connection script, and `openocd`
will automatically pick the right FTDI.
Another well known FTDI based probe is the [Olimex
ARM-USB-TINY-H](https://www.olimex.com/Products/ARM/JTAG/ARM-USB-TINY-H/)
(see the example `openocd-vexriscv` connection script for the Icoboard
`pqvexriscvicoboard.cfg`).

### Icoboarda (WIP)
This FPGA target is still a heavy work in progress.
The FPGA itself has more LUTs than the UP5K, however, doesn't feature
any DSPs.
Thus, the design will likely use a smaller multi-cycle multiplier to
implement the multiplication instruction.
The large SRAM blocks of the UP5K are also missing, however, the
Icoboard offers a 8MBit large SRAM chip, which will be used for
code **and** data.

### Xilinx 7 Series
The Digilent Arty A7 board uses the Xilinx Artix-7 XC7A35T (or XC7A100T
depending on the model), which will comfortably fit even larger variants
of the VexRiscv core).
The Arty board also exists in other variants using the Spartan-7 series
chip, which should also be large enough for the VexRiscv.
Similarly to the UP5K, the default design will use two separate 64kb
blocks for code and data each.

On the Arty Boards, the UART is exposed on the shared FPGA configuration
and UART USB port of the board.
The JTAG (for the VexRiscv core, **not** the JTAG for the FPGA chip) is
exposed on the PMOD JD connector.

| Function | Pin (PMOD JD) |
|----------|---------------|
| `TDO`    | 1             |
| `TCK`    | 3             |
| `TDI`    | 7             |
| `TMS`    | 8             |

If you use the Olimex ARM-USB-TINY-H, you'll also need to connect the
VREF.
Note, that the pinout is exactly the same as the [SiFive Freedom E310
Arty FPGA Dev Kit](https://github.com/sifive/freedom), except that
the `nRST`, `nTRST` remain unused.

## Internals

*TODO*: Write up some infos on the platforms, what features of VexRiscv
are enabled, how the memory architecture works, ...
