puts "Arguments: ${argv}"

proc has_arg_with_param {argv name} {
    # Find argument
    set _OPTIDX [lsearch -exact $argv $name]
    if {${_OPTIDX} < 0} {
        return false
    }

    set _OPTIDX [expr ${_OPTIDX} + 1]
    if {[llength $argv] <= $_OPTIDX} {
        puts "Missing parameter for argument $name!"
        exit 1
    }
    return [lindex $argv $_OPTIDX]
}

proc has_arg {argv name} {
    # Find argument
    set _OPTIDX [lsearch -exact $argv $name]
    return [expr ${_OPTIDX} >= 0]
}

set PROJECT_NAME [has_arg_with_param ${argv} "-board"]
if {${PROJECT_NAME} == false} {
    puts "Command line argument $name \[boardname\] required!"
    exit 1
}

if {![file exists "${PROJECT_NAME}.tcl"]} {
    puts "Board file ${PROJECT_NAME}.tcl missing!"
    exit 1
}

set CLOCK_FREQ [has_arg_with_param ${argv} -clock]
if {${CLOCK_FREQ} != false} {
    set CLOCK_PERIOD [expr  {1.0e9 / (${CLOCK_FREQ} * 1.0e6)}]
    puts "Clock Frequency set to ${CLOCK_FREQ} MHz (${CLOCK_PERIOD} ns period)"
}

source "${PROJECT_NAME}.tcl"

set SYNTH_CHECKPOINT [has_arg_with_param ${argv} -synth_dcp]
if {${SYNTH_CHECKPOINT} != false && ![file exists ${SYNTH_CHECKPOINT}]} {
    puts "Synth checkpoint ${SYNTH_CHECKPOINT} does not exist!"
    exit 1
}

set PNR_CHECKPOINT [has_arg_with_param ${argv} -pnr_dcp]
if {${PNR_CHECKPOINT} != false && ![file exists ${PNR_CHECKPOINT}]} {
    puts "Synth checkpoint ${PNR_CHECKPOINT} does not exist!"
    exit 1
}

set SOURCE_OVERWRITE [has_arg_with_param ${argv} -source]
if {${SOURCE_OVERWRITE} != false && ![file exists ${SOURCE_OVERWRITE}]} {
    puts "Sources don't exist!"
    exit 1
} elseif {${SOURCE_OVERWRITE} != false} {
    puts "Using source file ${SOURCE_OVERWRITE}"
    set SOURCES [list ${SOURCE_OVERWRITE}]
}

set CONSTR_OVERWRITE [has_arg_with_param ${argv} -constr]
if {${CONSTR_OVERWRITE} != false && ![file exists ${CONSTR_OVERWRITE}]} {
    puts "Constraint file don't exist!"
    exit 1
} elseif {${CONSTR_OVERWRITE} != false} {
    puts "Using constraint file ${CONSTR_OVERWRITE}"
    set CONSTRAINTS [list ${CONSTR_OVERWRITE}]
}

set MAXDSPS [has_arg_with_param ${argv} "-dsp_limit"]
if {${MAXDSPS} == false} {
    set MAXDSPS -1
} else {
    scan ${MAXDSPS} %d MAXDSPS
}

set OUTPUT_NAME [has_arg_with_param ${argv} "-out"]
if {${OUTPUT_NAME} == false} {
} else {
    set PROJECT_NAME ${OUTPUT_NAME}
}

# The sourced script should set the following variables
# SOURCES: List of source files
# CONSTRAINTS: List of constraint files
# TOPLEVEL: Toplevel module
# PARTNUMBER: Partnumber

set FAST [has_arg ${argv} -fast]
set PERF [has_arg ${argv} -perf]

set SYNTH_DIRECTIVE "AreaOptimized_high"
set OPT_DIRECTIVE "ExploreArea"
set PLACE_DIRECTIVE "ExtraTimingOpt"
set PHYS_DIRECTIVE "AggressiveExplore"
set ROUTE_DIRECTIVE "AggressiveExplore"

if {${FAST}} {
    set SYNTH_DIRECTIVE "RuntimeOptimized"
    set OPT_DIRECTIVE "RuntimeOptimized"
    set PLACE_DIRECTIVE "RuntimeOptimized"
    set PHYS_DIRECTIVE "RuntimeOptimized"
    set ROUTE_DIRECTIVE "RuntimeOptimized"
} elseif {${PERF}} {
    set SYNTH_DIRECTIVE "PerformanceOptimized"
    set OPT_DIRECTIVE "Explore"
    set PLACE_DIRECTIVE "ExtraTimingOpt"
    set PHYS_DIRECTIVE "AggressiveExplore"
    set ROUTE_DIRECTIVE "HigherDelayCost"
}

set QUIET {}

if {[has_arg ${argv} -quiet]} {
    set QUIET {"-quiet"}
}
