TARGETS ?= PQVexRiscvUP5K PQVexRiscvIcoboard

PQVexRiscvUP5K_DEVICE ?= up5k
PQVexRiscvIcoboard_DEVICE ?= hx8k

PQVexRiscvUP5K_PACKAGE ?= sg48
PQVexRiscvIcoboard_PACKAGE ?= ct256

PQVexRiscvUP5K_YOSYSFLAGS ?= -dsp
PQVexRiscvIcoboard_YOSYSFLAGS ?=

YOSYS = yosys
NEXTPNR = nextpnr-ice40
ICETIME = icetime
ICEPACK = icepack

XILINX_SYNTH_ARGS ?=

SCALA_SOURCES := $(shell find ../src -name "*.scala")

all: $(addsuffix .time,$(TARGETS)) $(addsuffix .bit,$(TARGETS))

%.v: ../build.sbt $(SCALA_SOURCES)
	cd ../;	sbt "runMain mupq.$(basename $@)"

%.json: %.v
	$(YOSYS) -q -p "synth_ice40 -top $(basename $<) -json $@ $($(basename $<)_YOSYSFLAGS)" $<

%.asc: %.json %.pcf
	$(NEXTPNR) --$($(basename $<)_DEVICE) --package $($(basename $<)_PACKAGE) --json $< --pcf $(filter %.pcf,$^) --asc $@

%.time: %.asc
	$(ICETIME) -tmd $($(basename $<)_DEVICE) $< | tee $@

%.bit: %.asc
	$(ICEPACK) $< $@

PQVexRiscvArtyA7.v: ../build.sbt $(SCALA_SOURCES)
	cd ../;	sbt "runMain mupq.PQVexRiscvArty --mul"
	mv PQVexRiscvArty.v $@

PQVexRiscvArtyS7.v: ../build.sbt $(SCALA_SOURCES)
	cd ../;	sbt "runMain mupq.PQVexRiscvArty --ram 128,64 --clk 12 --core 12"
	mv PQVexRiscvArty.v $@

PQVexRiscvArtyA7.mcs PQVexRiscvArtyA7.bit PQVexRiscvArtyA7.time PQVexRiscvArtyA7.util: PQVexRiscvArtyA7.v PQVexRiscvArtyA7.tcl synth.tcl
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs -board PQVexRiscvArtyA7 $(XILINX_SYNTH_ARGS)

PQVexRiscvArtyS7.mcs PQVexRiscvArtyS7.bit PQVexRiscvArtyS7.time PQVexRiscvArtyS7.util: PQVexRiscvArtyS7.v PQVexRiscvArtyS7.tcl synth.tcl
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs -board PQVexRiscvArtyS7 $(XILINX_SYNTH_ARGS)

clean:
	rm *.asc *.bit *.json *.time *.v *.dcp

.SECONDARY:
