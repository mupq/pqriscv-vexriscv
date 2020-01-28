ICESTORM_TARGETS ?= PQVexRiscvUP5K PQVexRiscvIcoboard
VIVADO_TARGETS ?= PQVexRiscvArtyA7

YOSYS = yosys
NEXTPNR = nextpnr-ice40
ICETIME = icetime
ICEPACK = icepack

XILINX_SYNTH_ARGS ?=

SCALA_SOURCES := $(shell find ../src -name "*.scala")

all: $(addsuffix .bit,$(ICESTORM_TARGETS)) $(addsuffix .mcs,$(VIVADO_TARGETS))

GENERATOR_FLAGS =
GENERATOR_NAME = $(basename $@)
GENERATED_FILE = $(GENERATOR_NAME).v

.SECONDEXPANSION:

%.v: ../build.sbt $(SCALA_SOURCES)
	cd ../;	sbt "runMain mupq.$(GENERATOR_NAME) $(GENERATOR_FLAGS)"
	if [ "$(GENERATED_FILE)" != "$@" ]; then \
		mv $(GENERATED_FILE) $@; \
	fi


TOPLEVEL_MODULE = $*

%.json %_yosys.log: %.v
	$(YOSYS) -q -l $*_yosys.log -p "synth_ice40 -top $(TOPLEVEL_MODULE) -json $@ $(YOSYSFLAGS)" $<

PCF_FILE = $*.pcf

%.asc %_pnr.log: %.json $$(PCF_FILE)
	$(NEXTPNR) -l $*_pnr.log --$(DEVICE) --package $(PACKAGE) --json $< --pcf $(PCF_FILE) --asc $@

%.bit: %.asc
	$(ICEPACK) $< $@

PQVexRiscvUP5K.v: GENERATOR_FLAGS=--mul
PQVexRiscvUP5K.json: YOSYSFLAGS=-dsp
PQVexRiscvUP5K.asc: PACKAGE=sg48
PQVexRiscvIcoboard.asc: PACKAGE=ct256
PQVexRiscvUP5K.asc: DEVICE=up5k
PQVexRiscvIcoboard.asc: DEVICE=hx8k

PQVexRiscvArtyA7.v: GENERATOR_NAME=PQVexRiscvArty
PQVexRiscvArtyS7.v: GENERATOR_NAME=PQVexRiscvArty
PQVexRiscvArtyA7.v: GENERATOR_FLAGS=--mul
PQVexRiscvArtyS7.v: GENERATOR_FLAGS=--mul

BOARD = $*

%.mcs %.time %.util: %.v $$(BOARD).tcl synth.tcl args.tcl
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs -board $(BOARD) $(XILINX_SYNTH_ARGS)

clean:
	rm -f *.asc *.bit *.json *.log *.v *.dcp *.mcs *.util *.time

.SECONDARY:
