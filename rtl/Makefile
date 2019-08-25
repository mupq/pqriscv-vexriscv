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

all: $(addsuffix .time,$(TARGETS)) $(addsuffix .bit,$(TARGETS))

%.v: ../build.sbt $(shell find ../src -name "*.scala")
	cd ../;	sbt "runMain mupq.$(basename $@)"

%.json: %.v
	$(YOSYS) -q -p "synth_ice40 -top $(basename $<) -json $@ $($(basename $<)_YOSYSFLAGS)" $<

%.asc: %.json %.pcf
	$(NEXTPNR) --$($(basename $<)_DEVICE) --package $($(basename $<)_PACKAGE) --json $< --pcf $(filter %.pcf,$^) --asc $@

%.time: %.asc
	$(ICETIME) -tmd $($(basename $<)_DEVICE) $< | tee $@

%.bit: %.asc
	$(ICEPACK) $< $@


PQVexRiscvArtyA7.v: ../build.sbt $(shell find ../src -name "*.scala")
	cd ../;	sbt "runMain mupq.PQVexRiscvArty"
	mv PQVexRiscvArty.v $@

PQVexRiscvArtyS7.v: ../build.sbt $(shell find ../src -name "*.scala")
	cd ../;	sbt "runMain mupq.PQVexRiscvArty --ram 128,64 --clk 12 --core 12"
	mv PQVexRiscvArty.v $@

PQVexRiscvArtyA7.bit PQVexRiscvArtyA7.time: PQVexRiscvArtyA7.v PQVexRiscvArtyA7.tcl synth.tcl
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs -board PQVexRiscvArtyA7

PQVexRiscvArtyS7.bit PQVexRiscvArtyS7.time: PQVexRiscvArtyS7.v PQVexRiscvArtyS7.tcl synth.tcl
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs -board PQVexRiscvArtyS7

clean:
	rm *.asc *.bit *.json *.time *.v *.dcp

.SECONDARY:
