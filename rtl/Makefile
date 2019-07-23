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

PQVexRiscvArty.bit PQVexRiscvArty.time: PQVexRiscvArty.v
	vivado -nojournal -log log.log -mode batch -source Arty-A7-fast.tcl

clean:
	rm *.asc *.bit *.json *.time

.SECONDARY:
