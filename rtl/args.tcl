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

if {![file exists "${PROJECT_NAME}.tcl"]} {
    puts "Board file ${PROJECT_NAME}.tcl missing!"
    exit 1
}

# The sourced script should set the following variables
# SOURCES: List of source files
# CONSTRAINTS: List of constraint files
# TOPLEVEL: Toplevel module
# PARTNUMBER: Partnumber
source "${PROJECT_NAME}.tcl"

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
