# PQVexRiscV
[VexRiscv](https://github.com/SpinalHDL/VexRiscv) based Target platforms
for the [pqriscv](https://github.com/mupq/pqriscv) project

## Introduction
The goal of this project is to provide some implement a simple
test-platform for the VexRiscv CPU, to be used for benchmarking and
experimenting with PQC scheme implementations.

## Setup
You'll need the following

* Java JDK **==** 1.8
* [SBT](https://www.scala-sbt.org) >= 1.2.8, the Scala Build Tool
* (For iCE40 based FPGAs) [Icestorm FPGA Toolchain](http://www.clifford.at/icestorm/), including [NextPNR](https://github.com/YosysHQ/nextpnr)
* (For the Simulator) [Verilator](https://www.veripool.org/wiki/verilator)
* (Optionally for Debugging) [OpenOCD for VexRiscv](https://github.com/SpinalHDL/openocd_riscv)

## Synthesis
This project (and VexRiscv) uses the SpinalHDL language. This is
essentially a DSL for hardware design on top of the Scala language,
which will create a generator for Verilog (or VHDL) which represents the
circuits described in SpinalHDL. So to create a bitstream for a FPGA,
you'll have to generate the verilog file first and then run synthesis.

Just run `sbt` in the project root and run any of the main classes to
generate the verilog source for one of the targets. The `rtl` folder
contains a Makefile to generate the bitstream for the FPGAs (the
Makefile won't run `sbt` to generate the Verilog). For example:

```sh
sbt "runMain mupq.PQVexRiscvUP5K"
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
./configure --prefix=/usr/local --program-suffix=-vexriscv --datarootdir=/usr/local/share/vexriscv --enable-maintainer-mode --disable-werror --enable-ft232r --enable-ftdi --enable-jtag_vpi --disable-aice --disable-amtjtagaccel --disable-armjtagew --disable-assert --disable-at91rm9200 --disable-bcm2835gpio --disable-buspirate --disable-cmsis-dap --disable-doxygen-html --disable-doxygen-pdf --disable-dummy --disable-ep93xx --disable-gw16012 --disable-imx_gpio --disable-jlink --disable-kitprog --disable-minidriver-dummy --disable-oocd_trace --disable-opendous --disable-openjtag --disable-osbdm --disable-parport --disable-parport-giveio --disable-parport-ppdev --disable-presto --disable-remote-bitbang --disable-rlink --disable-stlink --disable-sysfsgpio --disable-ti-icdi --disable-ulink --disable-usb-blaster --disable-usb-blaster-2 --disable-usbprog --disable-verbose-jtag-io --disable-verbose-usb-comms --disable-verbose-usb-io --disable-vsllink --disable-xds110 --disable-zy1000 --disable-zy1000-master
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

## Internals

*TODO*: Write up some infos on the platforms, what features of VexRiscv
are enabled, how the memory architecture works, ...
