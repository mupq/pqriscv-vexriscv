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
	[ -d gen ] || mkdir -p gen
	cd ../;	sbt "runMain mupq.$(GENERATOR_NAME) $(GENERATOR_FLAGS)"
	mv gen/$(GENERATED_FILE) $@
	if [ -e "gen/$(GENERATED_FILE:.v=.aux.v)" ]; then \
		cat "gen/$(GENERATED_FILE:.v=.aux.v)" >> $@; \
		rm "gen/$(GENERATED_FILE:.v=.aux.v)"; \
	fi
	find gen/ -name *.gen -exec mv -t ./ {} +

TOPLEVEL_MODULE = $(GENERATOR_NAME)

%.json %_yosys.log: %.v
	$(YOSYS) -q -l $*_yosys.log -p "synth_ice40 -top $(TOPLEVEL_MODULE) -json $@ $(YOSYSFLAGS)" $(filter %.v,$^)

PCF_FILE = $*.pcf

%.asc %_pnr.log: %.json $$(PCF_FILE)
	$(NEXTPNR) -l $*_pnr.log --$(DEVICE) --package $(PACKAGE) --json $< --pcf $(PCF_FILE) --asc $@

%.bit: %.asc
	$(ICEPACK) $< $@

BOARD = $*
XILINX_OUTPUT_NAME = $(BOARD)

CORE_FREQ = 100
CLOCK_FREQ = 100
CLOCK_PARAMS = \
	$(if $(CORE_FREQ),--core ${CORE_FREQ}) \
	$(if $(CLOCK_FREQ),--clk ${CLOCK_FREQ})

PQVexRiscvUP5K.v: GENERATOR_FLAGS=--mul
PQVexRiscvUP5K.json: YOSYSFLAGS=-dsp
PQVexRiscvUP5K.asc: PACKAGE=sg48
PQVexRiscvIcoboard.asc: PACKAGE=ct256
PQVexRiscvUP5K.asc: DEVICE=up5k
PQVexRiscvIcoboard.asc: DEVICE=hx8k

PQVexRiscvArtyA7.v: GENERATOR_NAME=PQVexRiscvArty
PQVexRiscvArtyS7.v: GENERATOR_NAME=PQVexRiscvArty
PQVexRiscvArtyA7.v: GENERATOR_FLAGS=--mul $(CLOCK_PARAMS)
PQVexRiscvArtyS7.v: GENERATOR_FLAGS=--mul $(CLOCK_PARAMS)

%.mcs %.time %.util: $$(if $$(SOURCE_FILE),$$(SOURCE_FILE),%.v) $$(BOARD).tcl synth.tcl args.tcl $$(CONSTRAINT_FILE)
	vivado -nojournal -nolog -mode batch -source synth.tcl -tclargs \
		-board $(BOARD) \
		-out $(XILINX_OUTPUT_NAME) \
		$(if $(CLOCK_FREQ),-clock $(CLOCK_FREQ)) \
		$(if $(CONSTRAINT_FILE),-constr $(CONSTRAINT_FILE)) \
		$(if $(SOURCE_FILE),-source $(SOURCE_FILE)) \
		$(XILINX_SYNTH_ARGS)

clean:
	rm -f *.asc *.bit *.json *.log *.v *.dcp *.mcs *.util *.time

.SECONDARY:
