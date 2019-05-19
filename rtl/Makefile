TARGETS ?= PQVexRiscvUP5K PQVexRiscvIcoboard

PQVexRiscvUP5K_DEVICE ?= up5k
PQVexRiscvIcoboard_DEVICE ?= hx8k

YOSYS = yosys
NEXTPNR = nextpnr-ice40
ICETIME = icetime
ICEPACK = icepack

TARGET_FREQ = 12

all: $(addsuffix .time,$(TARGETS)) $(addsuffix .bin,$(TARGETS))

# %.v: ../build.sbt $(shell find ../src -name "*.scala")
# 	cd ../;	sbt "runMain mupq.$(basename $@)"

%.json: %.v
	$(YOSYS) -q -p "synth_ice40 -top $(basename $<) -json $@" $<

%.asc: %.json %.pcf
	$(NEXTPNR) --$($(basename $<)_DEVICE) --json $< --pcf $(filter %.pcf,$^) --asc $@ --opt-timing

%.time: %.asc
	$(ICETIME) -tmd $($(basename $<)_DEVICE) $< | tee $@

%.bin: %.asc
	$(ICEPACK) $< $@

.SECONDARY:
